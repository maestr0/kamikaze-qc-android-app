package info.raszewski.adruino;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.content.SharedPreferences;
import android.hardware.SensorEvent;
import android.util.Log;

public class FlightControlEngine implements Runnable {

	private static final String TAG = "FCE";

	private boolean isFlying;
	private boolean isRemoteControled;
	private int thrust;
	private double cv;

	private int m4t;
	private int m3t;
	private int m2t;
	private int m1t;

	private float calRoll;
	private float calPitch;

	private float azimuth_angle;

	private float pitch_angle;

	private int correctionVectorMax = 0;

	private float roll_angle;

	private float calAzimuth;

	private FlightConfiguration flightConsoleConfig;

	private SharedPreferences sharedPrefs;

	public FlightControlEngine(SharedPreferences sharedPrefs) {
		this.sharedPrefs = sharedPrefs;
		correctionVectorMax = (Integer.parseInt(sharedPrefs.getString(
				"correction_max", "5")));
	}

	public void calibrate() {
		Log.v(TAG, "calibrating (zeroing)...");
		calAzimuth = azimuth_angle;
		calPitch = pitch_angle;
		calRoll = roll_angle;
	}

	public void setStatus(boolean isFlying) {
		this.isFlying = isFlying;
		Log.v(TAG, "IS FLYING=" + isFlying);
	}

	public boolean startRemoteControl() {
		isRemoteControled = true;
		Thread rcThread = new Thread(this);
		rcThread.setDaemon(true);
		rcThread.start();
		Log.d(TAG, "StartRemoteControl method called");
		return isRemoteControled;
	}

	public void stopRemoteControl() {
		this.isRemoteControled = false;
	}

	public void setBaseThrust(int thrust) {
		this.thrust = thrust;
		Log.v(TAG, "Thrust=" + thrust);
	}

	public void setCorrectionVector(int cv) {
		this.cv = cv / 10.0;
		Log.v(TAG, "CV=" + cv);
	}

	public void updateSensorStatus(SensorEvent event) {
		azimuth_angle = event.values[0];
		pitch_angle = event.values[1];
		roll_angle = event.values[2];
		calculateMotorThrust();
	}

	public void calculateMotorThrust() {
		if (isFlying)
			stabilizeFlight();
		else
			resetMotors();
	}

	private void resetMotors() {
		m1t = 0;
		m2t = 0;
		m3t = 0;
		m4t = 0;
	}

	private void stabilizeFlight() {
		ThrustMatrix tm = new ThrustMatrix();
		if (isRemoteControled)
			useFlightConfigurationFromRemoteServer(tm);
		calculatePitchCorrectionVector(tm);
		calculateRollCorrectionVector(tm);
		updateMotorThrust(tm);
	}

	private void useFlightConfigurationFromRemoteServer(ThrustMatrix tm) {
		if (flightConsoleConfig != null) {
			tm.m1 = flightConsoleConfig.tm.m1;
			tm.m2 = flightConsoleConfig.tm.m2;
			tm.m3 = flightConsoleConfig.tm.m3;
			tm.m4 = flightConsoleConfig.tm.m4;
		}
	}

	private void calculatePitchCorrectionVector(ThrustMatrix tm) {
		// pitch difference
		double v = cv * Math.abs(getPitchDiviation());
		if(v > correctionVectorMax)
			v = correctionVectorMax;
		
		if (getPitchDiviation() > 0) {
			tm.m3 += v;
			tm.m4 += v;
		} else {
			tm.m1 += v;
			tm.m2 += v;
		}
	}

	private void calculateRollCorrectionVector(ThrustMatrix tm) {
		double v;
		// roll difference
		v = cv * Math.abs(getRollDiviation());
		if(v > correctionVectorMax)
			v = correctionVectorMax;
		if (getRollDiviation() > 0) {
			tm.m2 += v;
			tm.m4 += v;
		} else {
			tm.m1 += v;
			tm.m3 += v;
		}
	}

	private void updateMotorThrust(ThrustMatrix tm) {
		m1t = (int) (thrust + tm.m1);
		m2t = (int) (thrust + tm.m2);
		m3t = (int) (thrust + tm.m3);
		m4t = (int) (thrust + tm.m4);
	}

	private float getRollDiviation() {
		return roll_angle - calRoll;
	}

	private float getPitchDiviation() {
		return pitch_angle - calPitch;
	}

	public int getM1Thrust() {
		return m1t;
	}

	public int getM2Thrust() {
		return m2t;
	}

	public int getM3Thrust() {
		return m3t;
	}

	public int getM4Thrust() {
		return m4t;
	}

	public boolean isFlying() {
		return isFlying;
	}

	@Override
	public void run() {
		Log.d(TAG, "Starting RC Thread");
		while (isRemoteControled) {
			String cfg = callConfigurationAPI();
			if (cfg != null) {
				flightConsoleConfig = new FlightConfiguration(cfg);
				setBaseThrust(flightConsoleConfig.baseThrust);
				setCorrectionVector(flightConsoleConfig.correctionVector);
			}
//			try {
//				Thread.sleep(1000);
//			} catch (InterruptedException e) {
//				Log.e(TAG,e.getMessage());
//			}
		}
		Log.d(TAG, "RC Thread STOPPED");
	}

	private String callConfigurationAPI() {
		try {
			int timeoutConnection = Integer.parseInt(sharedPrefs.getString(
					"timeout_connection", "5000"));
			HttpParams httpParameters = new BasicHttpParams();
			HttpConnectionParams.setConnectionTimeout(httpParameters,
					timeoutConnection);
			// in milliseconds which is the timeout for waiting for data.
			int timeoutSocket = Integer.parseInt(sharedPrefs.getString(
					"timeout_socket", "5000"));
			HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);

			DefaultHttpClient httpclient = new DefaultHttpClient(httpParameters);
			HttpPost httppost = new HttpPost(sharedPrefs.getString("api_url",
					"http://192.168.1.4:3000/configuration"));
			// Depends on your web service
			httppost.setHeader("Content-type", "application/json");

			InputStream inputStream = null;
			String result = null;
			HttpResponse response;
			response = httpclient.execute(httppost);
			HttpEntity entity = response.getEntity();

			inputStream = entity.getContent();
			// json is UTF-8 by default i beleive
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					inputStream, "UTF-8"), 8);
			StringBuilder sb = new StringBuilder();

			String line = null;
			while ((line = reader.readLine()) != null) {
				sb.append(line + "\n");
			}
			Log.v(TAG, sb.toString());
			return result = sb.toString();
			
		} catch (ClientProtocolException e) {
			e.printStackTrace();
			Log.e(TAG, e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			Log.e(TAG, "IO Exception " + (e!=null ? e.getMessage() :" MSG NULL"));
		}
		return null;
	}

	public int getBaseThrust() {
		return thrust;
	}

	public int getCorrectionVector() {
		return (int) cv;
	}
}
