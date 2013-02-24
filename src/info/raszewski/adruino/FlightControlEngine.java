package info.raszewski.adruino;

import android.hardware.SensorEvent;
import android.util.Log;

public class FlightControlEngine {

	private static final String TAG = "FCE";

	private boolean isFlying;
	private int thrust;
	private double cv;

	private int m4t;
	private int m3t;
	private int m2t;
	private int m1t;

	private float calAzimuth;
	private float calPitch;
	private float calRoll;

	private float azimuth_angle;

	private float pitch_angle;

	private float roll_angle;

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

	public void setThrust(int thrust) {
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
		if (isFlying)
			calculateThrust();
		else
			resetMotors();
	}

	private void resetMotors() {
		m1t = 0;
		m2t = 0;
		m3t = 0;
		m4t = 0;
	}

	private void calculateThrust() {

		double cv1 = 0;
		double cv2 = 0;
		double cv3 = 0;
		double cv4 = 0;

		// azimuth difference, TODO: rotation
		float aDiff = azimuth_angle - calAzimuth;

		// pitch difference
		float pDiff = pitch_angle - calPitch;
		double v = cv * Math.abs(pDiff);
		if (pDiff > 0) {
			cv3 += v;
			cv4 += v;
		} else {
			cv1 += v;
			cv2 += v;
		}

		// roll difference
		float rDiff = roll_angle - calRoll;
		v = cv * Math.abs(rDiff);
		if (rDiff > 0) {
			cv2 += v;
			cv4 += v;
		} else {
			cv1 += v;
			cv3 += v;
		}

		m1t = (int) (thrust + cv1);
		m2t = (int) (thrust + cv2);
		m3t = (int) (thrust + cv3);
		m4t = (int) (thrust + cv4);
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
}
