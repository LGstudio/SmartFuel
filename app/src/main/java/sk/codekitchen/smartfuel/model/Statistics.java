package sk.codekitchen.smartfuel.model;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Locale;

import sk.codekitchen.smartfuel.exception.UnknownUserException;

/**
 * @author Attila Večerek
 */
public final class Statistics {

	public TabData week;
	public TabData month;
	public TabData year;

	private Statistics(Context context)
			throws ParseException, UnknownUserException, JSONException {

		JSONObject stats = (new SFDB(context)).queryStats();
		this.week = new TabData(stats.getJSONObject(TabData.WEEK));
		this.month = new TabData(stats.getJSONObject(TabData.MONTH));
		this.year = new TabData(stats.getJSONObject(TabData.YEAR));
	}

	public static Statistics getInstance(Context context)
			throws ParseException, UnknownUserException, JSONException {

		return new Statistics(context);
	}

	class TabData {
		public static final String WEEK = "week";
		public static final String MONTH = "month";
		public static final String YEAR = "year";

		public ArrayList<ColumnData> col = new ArrayList<>();
		public int distance = 0;
		public int points = 0;
		public int successRate = 0;
		public int totalExpiredPoints = 0;

		private int correctDistance = 0;
		private int speedingDistance = 0;

		private TabData(JSONObject tab) throws JSONException {
			Iterator<?> keys = tab.keys();
			String key;
			ColumnData col;
			while(keys.hasNext()) {
				key = (String) keys.next();
				if (!tab.isNull(key)) {
					col = new ColumnData(key, tab.getJSONObject(key));
				}
				else {
					col = new ColumnData(key);
				}

				correctDistance += col.correctDistance;
				speedingDistance += col.speedingDistance;
				points += col.points;
				totalExpiredPoints += col.expiredPoints;
				this.col.add(col);
			}
			distance = correctDistance + speedingDistance;
			successRate = distance == 0 ?
					-1 : (int) Math.round(100 * (correctDistance / (double) distance));
		}

		class ColumnData {
			public int index;
			public String key;

			public int points;
			public int correctDistance;
			public int speedingDistance;
			public int expiredPoints;

			private ColumnData(String index) {
				setIndexAndKey(index);
				points = 0;
				correctDistance = 0;
				speedingDistance = 0;
				expiredPoints = 0;
			}

			private ColumnData(String index, JSONObject col) throws JSONException {
				setIndexAndKey(index);
				points = col.getInt("points");
				correctDistance = col.getInt("correct_dist");
				speedingDistance = col.getInt("speeding_dist");
				expiredPoints = col.getInt("total_expired");
			}

			private void setIndexAndKey(String index) {
				this.index = Integer.valueOf(index);
				Calendar cal = Calendar.getInstance();
				Locale locale = Locale.getDefault();

				switch (index) {
					case WEEK:
						cal.set(Calendar.DAY_OF_WEEK, this.index);
						key = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, locale);
						break;
					case MONTH:
						cal.set(Calendar.WEEK_OF_YEAR, this.index);
						key = cal.getDisplayName(Calendar.WEEK_OF_MONTH, Calendar.SHORT, locale);
						break;
					case YEAR:
						cal.set(Calendar.MONTH, this.index);
						key = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, locale);
						break;
				}
			}
		}
	}

	public static final class VIEW {
		public static final String NAME = "statistics";

		/**
		 * (strftime('%j', date(MyDate, '-3 days', 'weekday 4')) - 1) / 7 + 1 represent the ISOWeekNumber
		 *
		 * @see <a href="http://stackoverflow.com/questions/15082584/sqlite-return-wrong-week-number-for-2013#answer-15511864">Stackoverflow sqlite week number issue</a>
		 */
		public static final String CREATE =
				"CREATE VIEW " + NAME + " AS " +
						"SELECT SUM(points) as " + COLUMN.POINTS + "," +
						"ROUND(SUM(correct_dist), 1) as " + COLUMN.CORRECT_DISTANCE + "," +
						"ROUND(SUM(speeding_dist), 1) as " + COLUMN.SPEEDING_DISTANCE + "," +
						"SUM((points - spent)*expired) as " + COLUMN.TOTAL_EXPIRED + "," +
						"strftime('%u', created_at) as " + COLUMN.DAY + ", " + //returns days of week (1-7) starting with Monday
						"NULL as " + COLUMN.WEEK + "," +
						"NULL as " + COLUMN.MONTH + "\n" +
						"FROM `" + SmartFuelActivity.TABLE.NAME + "`\n" +
						"WHERE date(" + SmartFuelActivity.TABLE.COLUMN.CREATED_AT + ") BETWEEN date('now', 'weekday 1', '-7 days') AND date('now', 'weekday 1', '-1 day')\n" +
						"GROUP BY " + COLUMN.DAY + "\n" +

						"UNION ALL\n" +

						"SELECT SUM(points) as " + COLUMN.POINTS + "," +
						"ROUND(SUM(correct_dist), 1) as " + COLUMN.CORRECT_DISTANCE + "," +
						"ROUND(SUM(speeding_dist), 1) as " + COLUMN.SPEEDING_DISTANCE + "," +
						"SUM((points - spent)*expired) as " + COLUMN.TOTAL_EXPIRED + "," +
						"NULL as " + COLUMN.DAY + ", " +
						"(strftime('%j', date(created_at, '-3 days', 'weekday 4')) - 1) / 7 + 1 as " + COLUMN.WEEK + "," + //returns the ISOWeekNumbers
						"NULL as " + COLUMN.MONTH + "\n" +
						"FROM `" + SmartFuelActivity.TABLE.NAME + "`\n" +
						"WHERE date(" + SmartFuelActivity.TABLE.COLUMN.CREATED_AT + ") BETWEEN date('now', 'start of month') AND date('now', 'start of month', '+1 month', '-1 day')\n" +
						"GROUP BY " + COLUMN.WEEK + "\n" +

						"UNION ALL\n" +

						"SELECT SUM(points) as " + COLUMN.POINTS + "," +
						"ROUND(SUM(correct_dist), 1) as " + COLUMN.CORRECT_DISTANCE + "," +
						"ROUND(SUM(speeding_dist), 1) as " + COLUMN.SPEEDING_DISTANCE + "," +
						"SUM((points - spent)*expired) as " + COLUMN.TOTAL_EXPIRED + "," +
						"NULL as " + COLUMN.DAY + ", " +
						"NULL as " + COLUMN.WEEK + "," +
						"strftime('%m', created_at) as " + COLUMN.MONTH + "\n" + //returns the months (01-12)
						"FROM `" + SmartFuelActivity.TABLE.NAME + "`\n" +
						"WHERE date(" + SmartFuelActivity.TABLE.COLUMN.CREATED_AT + ") BETWEEN date('now', 'start of year') AND date('now', 'start of year', '+1 year', '-1 day')\n" +
						"GROUP BY " + COLUMN.MONTH;

		public static final String DROP = "DROP VIEW IF EXISTS " + NAME;

		public static final class COLUMN {
			public static final String POINTS = SmartFuelActivity.TABLE.COLUMN.POINTS;
			public static final String CORRECT_DISTANCE = SmartFuelActivity.TABLE.COLUMN.CORRECT_DISTANCE;
			public static final String SPEEDING_DISTANCE = SmartFuelActivity.TABLE.COLUMN.SPEEDING_DISTANCE;
			public static final String TOTAL_EXPIRED = "total_expired";
			public static final String DAY = "day";
			public static final String WEEK = "week";
			public static final String MONTH = "month";
		}
	}
}