package info.raszewski.adruino;

import org.json.JSONException;
import org.json.JSONObject;

public class FlightConfiguration {

	public int baseThrust;
	public int correctionVector;
	public int correctionLimit;
	public ThrustMatrix tm;

	public FlightConfiguration(String configuration) {
		tm = new ThrustMatrix();
		inflate(configuration);
	}

	private void inflate(String configuration) {
		try {
			JSONObject jObject = new JSONObject(configuration);
			tm.m1 = jObject.optInt("motor1", 666);
			tm.m2 = jObject.optInt("motor2", 0);
			tm.m3 = jObject.optInt("motor3", 0);
			tm.m4 = jObject.optInt("motor4", 0);

			baseThrust = findBaseThrust(tm);
			correctionVector = jObject.optInt("correction_vector", 0);
			correctionLimit = jObject.optInt("correction_limit", 0);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private int findBaseThrust(ThrustMatrix matrix) {
		double min = 200;
		if (matrix.m1 < min)
			min = matrix.m1;
		if (matrix.m2 < min)
			min = matrix.m2;
		if (matrix.m3 < min)
			min = matrix.m3;
		if (matrix.m4 < min)
			min = matrix.m4;

		matrix.m1 -= min;
		matrix.m2 -= min;
		matrix.m3 -= min;
		matrix.m4 -= min;

		return (int) min;
	}
}
