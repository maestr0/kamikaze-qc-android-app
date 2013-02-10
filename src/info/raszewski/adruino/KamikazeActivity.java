package info.raszewski.adruino;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.ToggleButton;

@TargetApi(11)
public class KamikazeActivity extends Activity implements Runnable,
		OnSeekBarChangeListener, SensorEventListener {

	private static final String TAG = "FCE";

	private static final String ACTION_USB_PERMISSION = "info.raszewski.adruino.KamikazeActivity.action.USB_PERMISSION";

	private UsbManager mUsbManager;
	private PendingIntent mPermissionIntent;
	private boolean mPermissionRequestPending;
	private SensorManager mSensorManager;
	private Sensor mOrientation;

	private ProgressBar azimuth_bar;
	private ProgressBar pitch_bar;
	private ProgressBar roll_bar;

	private TextView aVal;
	private TextView pVal;
	private TextView rVal;

	private Button calibrate;
	private ToggleButton mainSwitch;
	private ToggleButton usbStatus;

	private ProgressBar thrustBar1;
	private ProgressBar thrustBar2;
	private ProgressBar thrustBar3;
	private ProgressBar thrustBar4;

	private TextView thrustText1;
	private TextView thrustText2;
	private TextView thrustText3;
	private TextView thrustText4;

	private SeekBar thrustSlider;
	private SeekBar correctionVectorSlider;

	UsbAccessory mAccessory;
	ParcelFileDescriptor mFileDescriptor;
	FileInputStream mInputStream;
	FileOutputStream mOutputStream;

	private TextView tempValue;

	private Handler handler;

	protected boolean isOn;

	private FlightControlEngine fce;

	public static final byte LED_RED_COMMAND = 0;
	public static final byte LED_GREEN_COMMAND = 1;
	public static final byte LED_BLUE_COMMAND = 2;

	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) {
				synchronized (this) {
					UsbAccessory accessory = (UsbAccessory) intent
							.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);

					if (intent.getBooleanExtra(
							UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
						if (accessory != null) {
							// call method to set up accessory communication
							openAccessory(accessory);
						}
					} else {
						Log.d(TAG, "permission denied for accessory "
								+ accessory);
					}
				}
			}

			if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
				UsbAccessory accessory = (UsbAccessory) intent
						.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
				if (accessory != null) {
					// call your method that cleans up and closes communication
					// with the accessory
					closeAccessory();
				}
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		fce = new FlightControlEngine();

		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
				ACTION_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mUsbReceiver, filter);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.main);
        
		azimuth_bar = (ProgressBar) findViewById(R.id.azimuth_bar);
		roll_bar = (ProgressBar) findViewById(R.id.roll_bar);
		pitch_bar = (ProgressBar) findViewById(R.id.pitch_bar);

		aVal = (TextView) findViewById(R.id.a_val);
		rVal = (TextView) findViewById(R.id.r_val);
		pVal = (TextView) findViewById(R.id.p_val);

		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mOrientation = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

		thrustText1 = (TextView) findViewById(R.id.thrustText1);
		thrustText2 = (TextView) findViewById(R.id.thrustText2);
		thrustText3 = (TextView) findViewById(R.id.thrustText3);
		thrustText4 = (TextView) findViewById(R.id.thrustText4);

		thrustBar1 = (ProgressBar) findViewById(R.id.thrustBar1);
		thrustBar2 = (ProgressBar) findViewById(R.id.thrustBar2);
		thrustBar3 = (ProgressBar) findViewById(R.id.thrustBar3);
		thrustBar4 = (ProgressBar) findViewById(R.id.thrustBar4);

		thrustSlider = (SeekBar) findViewById(R.id.thrust_slider);
		correctionVectorSlider = (SeekBar) findViewById(R.id.correction_vector_slider);
		mainSwitch = (ToggleButton) findViewById(R.id.mainSwitch);
		usbStatus = (ToggleButton) findViewById(R.id.usbStatus);
		calibrate = (Button) findViewById(R.id.calibrateButton);

		fce.setThrust(thrustSlider.getProgress());
		fce.setCorrectionVector(correctionVectorSlider.getProgress());

		enableControls(false);
		handler = new Handler();

		initListeners();
		thrustBar1.setKeepScreenOn(true);
	}

	private void initListeners() {
		mainSwitch
				.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						fce.setStatus(isChecked);
					}
				});

		calibrate.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				fce.calibrate();
			}
		});

		correctionVectorSlider
				.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

					@Override
					public void onStopTrackingTouch(SeekBar seekBar) {
						// TODO Auto-generated method stub

					}

					@Override
					public void onStartTrackingTouch(SeekBar seekBar) {
						// TODO Auto-generated method stub

					}

					@Override
					public void onProgressChanged(SeekBar seekBar,
							int progress, boolean fromUser) {
						fce.setCorrectionVector(progress);
					}
				});

		thrustSlider.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub

			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub

			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				fce.setThrust(progress);
			}
		});
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		if (mAccessory != null) {
			return mAccessory;
		} else {
			return super.onRetainNonConfigurationInstance();
		}
	}

	@Override
	public void onResume() {
		super.onResume();

		log("Resumed");

		if (mFileDescriptor != null && mInputStream != null
				&& mOutputStream != null) {
			log("IO streams OK in OnResume");
			return;
		}

		UsbAccessory[] accessories = mUsbManager.getAccessoryList();
		if(accessories!=null){
			log("Accessory LIST SIZE=" + accessories.length);
		}
		UsbAccessory accessory = (accessories == null ? null : accessories[0]);
		if (accessory != null) {
			if (mUsbManager.hasPermission(accessory)) {
				log("Opening accessory via onResume");
				openAccessory(accessory);
			} else {
				String log = "No Permission for accessory on Resume";
				log(log);
				Log.e(TAG, log);
				synchronized (mUsbReceiver) {
					if (!mPermissionRequestPending) {
						mUsbManager.requestPermission(accessory,
								mPermissionIntent);
						mPermissionRequestPending = true;
					}
				}
			}
		} else {
			Log.d(TAG, "mAccessory is null");
		}

		mSensorManager.registerListener(this, mOrientation,
				SensorManager.SENSOR_DELAY_NORMAL);
	}

	@Override
	public void onPause() {
		super.onPause();
		log("Paused");
		closeAccessory();
		enableControls(false);
		mSensorManager.unregisterListener(this);
	}

	@Override
	public void onDestroy() {
		unregisterReceiver(mUsbReceiver);
		log("Destroyed");
		super.onDestroy();
	}

	private void openAccessory(UsbAccessory accessory) {
		mFileDescriptor = mUsbManager.openAccessory(accessory);
		if (mFileDescriptor != null) {
			mAccessory = accessory;
			FileDescriptor fd = mFileDescriptor.getFileDescriptor();
			mInputStream = new FileInputStream(fd);
			mOutputStream = new FileOutputStream(fd);
			String msg = "accessory opened ;)";
			usbStatus.setChecked(true);
			usbStatus.setBackgroundColor(Color.GREEN);
			Log.d(TAG, msg);
			log(msg);
		} else {
			usbStatus.setChecked(false);
			usbStatus.setBackgroundColor(Color.WHITE);
			String msg = "accessory open fail";
			Log.d(TAG, msg);
			log(msg);
		}
	}

	private void closeAccessory() {
		log("Closing accessory");
		try {
			if (mFileDescriptor != null) {
				mFileDescriptor.close();
			}
		} catch (IOException e) {
		} finally {
			mFileDescriptor = null;
			mAccessory = null;
		}
		usbStatus.setChecked(false);
		usbStatus.setBackgroundColor(Color.WHITE);
	}

	private void enableControls(boolean b) {
		calibrate.setActivated(b);
		thrustSlider.setActivated(b);
		correctionVectorSlider.setActivated(b);
	}

	public void sendCommand(byte command, byte target, int value) {
		byte[] buffer = new byte[3];
		if (value > 255)
			value = 255;

		buffer[0] = command;
		buffer[1] = target;
		buffer[2] = (byte) value;
		if (mOutputStream != null && buffer[1] != -1) {
			try {
				mOutputStream.write(buffer);
			} catch (IOException e) {
				Log.e(TAG, "write failed", e);
			}
		}
	}

	public void run() {
		int retreived = 0;
		final byte[] buffer = new byte[16384];
		int i;

		while (retreived >= 0) {
			try {
				retreived = mInputStream.read(buffer);
				System.out.println("From Arduino=" + retreived + " Buffer="
						+ buffer);
			} catch (IOException e) {
				break;
			}

			i = 0;
			while (i < retreived) {
				int bufferLength = retreived - i;
				final int index = i;
				switch (buffer[i]) {

				default: {

					// updateTemperature(buffer, index);

					i = bufferLength;
				}
					break;
				}
			}

		}
	}

	private void log(String log) {
		// if (logField.getLineCount() > 20) {
		// logField.getText().clear();
		// }
		// logField.append(log + "\n");
	}

	@Override
	public void onProgressChanged(SeekBar arg0, int progress, boolean arg2) {
	}

	@Override
	public void onStartTrackingTouch(SeekBar arg0) {
		// We do nothing
	}

	@Override
	public void onStopTrackingTouch(SeekBar arg0) {
		// we do nothing
	}

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (fce.isFlying()) {
			float azimuth_angle = event.values[0];
			float pitch_angle = event.values[1];
			float roll_angle = event.values[2];

			fce.updateSensorStatus(event);

			roll_bar.setProgress((int) (roll_angle + 90));
			pitch_bar.setProgress((int) (pitch_angle + 180));
			azimuth_bar.setProgress((int) (azimuth_angle));

			aVal.setText(azimuth_angle + "°");
			pVal.setText(pitch_angle + "°");
			rVal.setText(roll_angle + "°");

			int m1t = fce.getM1Thrust();
			int m2t = fce.getM2Thrust();
			int m3t = fce.getM3Thrust();
			int m4t = fce.getM4Thrust();

			updateMotorThrustView(m1t, m2t, m3t, m4t);

			// TODO: send thrust config to Arduino
			sendMotorThrustToArduino(m1t, m2t, m3t, m4t);
		}
	}

	private void sendMotorThrustToArduino(int m1t, int m2t, int m3t, int m4t) {

		byte[] buffer = new byte[4];
		buffer[0] = (byte) m1t;
		buffer[1] = (byte) m2t;
		buffer[2] = (byte) m3t;
		buffer[3] = (byte) m4t;

		if (mOutputStream != null) {
			try {
				mOutputStream.write(buffer);
				mOutputStream.flush();
				log("Sent: " + buffer[0]);
				usbStatus.setBackgroundColor(Color.GREEN);
			} catch (IOException e) {
				String msg = "write failed";
				usbStatus.setBackgroundColor(Color.RED);
				Log.e(TAG, msg, e);
				log(msg);
			}
		} 
//		else {
//			String msg = "Output Stream NULL";
//			Log.e(TAG, msg);
//			log(msg);
//		}
	}

	private void updateMotorThrustView(int m1t, int m2t, int m3t, int m4t) {
		thrustBar1.setProgress(m1t);
		thrustBar2.setProgress(m2t);
		thrustBar3.setProgress(m3t);
		thrustBar4.setProgress(m4t);

		thrustText1.setText("" + m1t);
		thrustText2.setText("" + m2t);
		thrustText3.setText("" + m3t);
		thrustText4.setText("" + m4t);

	}

}
