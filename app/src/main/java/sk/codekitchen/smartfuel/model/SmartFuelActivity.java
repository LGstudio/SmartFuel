package sk.codekitchen.smartfuel.model;

import android.content.ContentValues;
import android.content.Context;
import android.location.Location;
import android.os.Environment;

import com.tomtom.lbs.sdk.geolocation.ReverseGeocodeData;
import com.tomtom.lbs.sdk.geolocation.ReverseGeocodeListener;
import com.tomtom.lbs.sdk.geolocation.ReverseGeocodeOptionalParameters;
import com.tomtom.lbs.sdk.geolocation.ReverseGeocoder;
import com.tomtom.lbs.sdk.util.Coordinates;
import com.tomtom.lbs.sdk.util.SDKContext;

import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import sk.codekitchen.smartfuel.exception.UnknownUserException;
import sk.codekitchen.smartfuel.util.GPXGenerator;

/**
 * Class, that handles the activity recording.
 * Activities should be recorded every time due
 * to possible lack of connectivity to the internet.
 * @author Attila Večerek
 */
public class SmartFuelActivity {

	protected static final String API_KEY = "xzu3bpa7jeqpkcu8ncp48593";
	protected RoadInfoListener TTlistener;
	protected ReverseGeocodeOptionalParameters TTparams;

	protected static final int POINTS_PER_KM = 1;
	protected static final float DISTANCE_TO_GET_POINTS = 1000f; //in meters
	protected static final float MPS_TO_KMH = 3.6f;
	protected static final float ROAD_INTERVAL = 50f; //in meters
	protected static final float HIGHWAY_INTERVAL = 200f; //in meters

	protected int speedLimit = 0; //in kmph
	protected String roadType;
	protected Location curLoc = null;
	protected Location prevLoc = null;
	protected float nextSpeedLimitCall = ROAD_INTERVAL; //meters

	protected Vector<Location> locations;

	protected float totalDistance = 0f; //in meters
	protected float progressCounter = 0f; //cyclic distance counter in meters
	protected float correctDistance = 0f; //in meters
	protected float speedingDistance = 0f; //in meters
	protected int points;
	protected int userID;

	protected long id;
	protected Context ctx;
	protected SFDB sfdb;

	protected boolean connectionAborted = false;

	public SmartFuelActivity(Context ctx)
			throws ParseException, UnknownUserException {

		this.ctx = ctx;
		sfdb = new SFDB(ctx);
		userID = sfdb.getUserID();

		//API Key
		SDKContext.setDeveloperKey(API_KEY);
		//TomTom Config
		TTlistener = new RoadInfoListener();
		TTparams = new ReverseGeocodeOptionalParameters();
		TTparams.type = ReverseGeocodeOptionalParameters.REVERSE_TYPE_NATIONAL;
	}

	public void setAbortedConnection() { connectionAborted = true; }

	public void addRecord(Location location) {
		locations.add(location);

		prevLoc = curLoc;
		curLoc = location;

		if (speedLimit == 0 || totalDistance >= nextSpeedLimitCall) {
			updateSpeedLimit(location.getLatitude(), location.getLongitude());
		}

		float distDiff = computeDistance();

		if(speedLimit != 0) {
			if (curLoc.getSpeed() * MPS_TO_KMH <= speedLimit) {
				addCorrectDistance(distDiff);
			} else {
				addSpeedingDistance(distDiff);
			}
		} else {
			addTotalDistance(distDiff);
		}
	}

	public void resetLocations() { prevLoc = curLoc= null; }

	/**
	 * Returns the distance between the {@code prevLoc} and {@code curLoc}
	 *
	 * @return distance in meters
	 */
	protected float computeDistance() {
		if(prevLoc != null)
			return prevLoc.distanceTo(curLoc);

		return 0f;
	}

	public int getPercentage() {
		return Math.round(100*(progressCounter/DISTANCE_TO_GET_POINTS));
	}

	public void addTotalDistance(float dist) { totalDistance += dist; }

	public void addCorrectDistance(float dist) {
		this.addTotalDistance(dist);
		correctDistance += dist;
		progressCounter += dist;
		if(progressCounter >= DISTANCE_TO_GET_POINTS) {
			progressCounter -= DISTANCE_TO_GET_POINTS;
			points += POINTS_PER_KM;
		}
	}

	public void addSpeedingDistance(float dist) {
		this.addTotalDistance(dist);
		speedingDistance += dist;
		progressCounter = 0f;
	}

	protected long insertDBActivity() throws JSONException {
		JSONObject activity = new JSONObject();
		activity.put(TABLE.COLUMN.CORRECT_DISTANCE, correctDistance);
		activity.put(TABLE.COLUMN.SPEEDING_DISTANCE, speedingDistance);
		activity.put(TABLE.COLUMN.POINTS, points);

		sfdb.saveData(TABLE.NAME, activity, SFDB.ORIGIN_LOCAL);
		return sfdb.lastInsertedId();
	}

	public void saveActivity()
			throws JSONException, ParserConfigurationException {

		GPXGenerator gpx = new GPXGenerator(ctx, locations);
		gpx.createXML();

		if (connectionAborted) {
			gpx.save();
		} else {
			gpx.save(insertDBActivity());
		}
	}

	protected long lazySave() throws JSONException {
		return insertDBActivity();
	}

	/**
	 * Fires TomTom's reverseGeocoder to check the road's
	 * speed limit.
	 *
	 * @param lat latitude
	 * @param lon longitude
	 */
	protected void updateSpeedLimit(double lat, double lon) {
		ReverseGeocoder.reverseGeocode(new Coordinates(lat, lon), TTparams, TTlistener, null);
	}

	public static void evaluatePendingActivities(Context ctx)
			throws ParseException, UnknownUserException,
			ParserConfigurationException, SAXException, IOException,
			JSONException {

		File pendingActivitiesDir = new File(Environment.getDataDirectory()
				+ GPXGenerator.PENDING_DIR);

		if (pendingActivitiesDir.exists()) {
			File[] dirFiles = pendingActivitiesDir.listFiles();
			SmartFuelActivity roadActivity;
			for (File pending : dirFiles) {
				roadActivity  = new SmartFuelActivity(ctx);
				GPXGenerator gpx = new GPXGenerator(ctx, pending);
				Vector<Location> locations = gpx.getLocations();
				for (Location loc : locations) {
					roadActivity.addRecord(loc);
				}
				//save as lazy evaluated activity
				long id = roadActivity.lazySave();
				//renames and moves file to gpx routes directory
				boolean result = pending.renameTo(new File(Environment.getDataDirectory()
						+ GPXGenerator.ACTIVITIES_DIR, gpx.getFileName(id)));
				if (!result)
					throw new IOException("File couldn't be moved");
			}
		}
	}

	/**
	 * Listens to the TomTom's Reversegeocoder being called.
	 * Sets and displays the current road's speed limit and
	 * road type. Based on the road type it sets the next
	 * checkpoint to check the speed limit again, thus decreasing
	 * the number of API calls - manages the network load.
	 *
	 * @author Attila Večerek
	 */
	class RoadInfoListener implements ReverseGeocodeListener {

		@Override
		public void handleReverseGeocode(Vector<ReverseGeocodeData> data, Object payload) {
			if(data != null && data.size() > 0) {
				ReverseGeocodeData result = data.elementAt(0);
				speedLimit = result.maxSpeedKph;
				//mainActivity.refreshSpeedLimit(speedLimit);
				roadType = result.roadType;
				nextSpeedLimitCall += roadType.equals("Motorway") ||
						roadType.equals("MajorRoad") ||
						roadType.equals("InternationalRoad") ? HIGHWAY_INTERVAL : ROAD_INTERVAL;
			}
		}
	}

	public static class TABLE {

		public static final String NAME = "activities";

		public static final String CREATE =
				"CREATE TABLE " + NAME + " (" +
						COLUMN.ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
						COLUMN.CORRECT_DISTANCE + " REAL," +
						COLUMN.SPEEDING_DISTANCE + " REAL," +
						COLUMN.CREATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
						COLUMN.POINTS + " INTEGER," +
						COLUMN.EXPIRED + " INTEGER DEFAULT 0," +
						COLUMN.SPENT + " INTEGER DEFAULT 0," +
						COLUMN.SYNCHRONIZED + " INTEGER DEFAULT 0" +
						");";

		public static final String DROP = "DROP TABLE IF EXISTS " + NAME;


		/**
		 * {@code _synchronized} defaults to false
		 * @see TABLE#getContentValues(JSONObject, Boolean)
		 */
		public static ContentValues getContentValues(JSONObject data)
				throws JSONException {

			return getContentValues(data, false);
		}

		/**
		 * Returns the user activity's content values based on the json object and the synchronization preference.
		 *
		 * @param data contains the user activity's data
		 * @param _synchronized synchronization preference, that marks the database row as synchronized
		 *                      with the server's DB
		 * @return the user activity's content values
		 * @throws JSONException if the json object could not be read properly
		 * @since 1.0
		 */
		public static ContentValues getContentValues(JSONObject data, Boolean _synchronized)
				throws JSONException {

			ContentValues cv = new ContentValues();
			if (data.has(COLUMN.ID)) cv.put(TABLE.COLUMN.ID, data.getInt(COLUMN.ID));
			if (data.has(COLUMN.CORRECT_DISTANCE)) cv.put(COLUMN.CORRECT_DISTANCE, data.getDouble(COLUMN.CORRECT_DISTANCE));
			if (data.has(COLUMN.SPEEDING_DISTANCE)) cv.put(COLUMN.SPEEDING_DISTANCE, data.getDouble(COLUMN.SPEEDING_DISTANCE));
			if (data.has(COLUMN.CREATED_AT)) cv.put(COLUMN.CREATED_AT, data.getString(COLUMN.CREATED_AT));
			if (data.has(COLUMN.POINTS)) cv.put(COLUMN.POINTS, data.getInt(COLUMN.POINTS));
			if (data.has(COLUMN.EXPIRED)) cv.put(COLUMN.EXPIRED, data.getInt(COLUMN.EXPIRED));
			if (data.has(COLUMN.SPENT)) cv.put(COLUMN.SPENT, data.getInt(COLUMN.SPENT));
			cv.put(COLUMN.SYNCHRONIZED, _synchronized ? 1 : 0);

			return cv;
		}

		public static class COLUMN {
			public static final String ID = "id";
			public static final String CORRECT_DISTANCE = "correct_dist";
			public static final String SPEEDING_DISTANCE = "speeding_dist";
			public static final String CREATED_AT = "created_at";
			public static final String POINTS = "points";
			public static final String EXPIRED = "expired";
			public static final String SPENT = "spent";
			public static final String SYNCHRONIZED  = "synchronized";
		}
	}

}
