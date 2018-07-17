package com.unb.hci.android_ble;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by Ian on 2016-05-10.
 */
public class DBHelper extends SQLiteOpenHelper {
    private static DBHelper sInstance;

    public static final String DATABASE_NAME = "Cane.db";
    public static final String CANE_TABLE_ANALYTICS = "caneAnalytics";    //Hourly averages
    public static final String CANE_TABLE_FULL = "caneFull";      //All raw data and angles
    public static final String CANE_TABLE_SHORT = "caneShort";      //All raw data and angles
    public static final String CANE_COLUMN_TIME = "time";
    public static final String CANE_COLUMN_FORCE = "force";
    public static final String CANE_COLUMN_ACC_X = "acc_x";
    public static final String CANE_COLUMN_ACC_Y = "acc_y";
    public static final String CANE_COLUMN_ACC_Z = "acc_z";
    public static final String CANE_COLUMN_GYRO_X = "gyro_x";
    public static final String CANE_COLUMN_GYRO_Y = "gyro_y";
    public static final String CANE_COLUMN_GYRO_Z = "gyro_z";
    public static final String CANE_COLUMN_ANGLE_PITCH = "pitch";
    public static final String CANE_COLUMN_ANGLE_ROLL = "roll";
    public static final String CANE_COLUMN_FORCE_VARIANCE = "forceVariance";
    public static final String CANE_COLUMN_PITCH_VARIANCE = "pitchVariance";
    public static final String CANE_COLUMN_ROLL_VARIANCE = "rollVariance";
    public static final String CANE_COLUMN_FORCE_MAX = "forceMax";
    public static final String CANE_COLUMN_ROLL_MEAN = "rollMean";
    public static final String TAG = "DBError";
    //private SQLiteDatabase db = null;

    private DBHelper(Context context, String databaseName) {
        //super(context, Environment.getExternalStorageDirectory().getAbsolutePath()
        //       + File.separator+DATABASE_NAME,null,1);
        super(context, databaseName, null, 1);
        //db = this.getReadableDatabase();
    }

    public static synchronized DBHelper getInstance(Context context) {
        // Use the application context, which will ensure that you
        // don't accidentally leak an Activity's context.
        // See this article for more information: http://bit.ly/6LRzfx
        if (sInstance == null) {
            /*File[] files = context.getExternalFilesDirs(null);
            File f = new File(files[files.length-1],DATABASE_NAME);*/
            String dbPath = context.getDatabasePath("Placeholder").getParentFile().getPath();
            sInstance = new DBHelper(context.getApplicationContext(), dbPath+File.separator+DATABASE_NAME);

        }
        return sInstance;
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + CANE_TABLE_ANALYTICS + "(time long primary key, forceMax double, rollMean double, forceVariance double," +
                "pitchVariance double, rollVariance double)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + CANE_TABLE_FULL + "(time long primary key, force double, acc_x double, acc_y double, acc_z double," +
                "gyro_x double, gyro_y double, gyro_z double, pitch double, roll double)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + CANE_TABLE_SHORT + "(time long primary key, force double, acc_x double, acc_y double, acc_z double," +
                "gyro_x double, gyro_y double, gyro_z double, pitch double, roll double)");
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + CANE_TABLE_ANALYTICS);
        db.execSQL("DROP TABLE IF EXISTS " + CANE_TABLE_FULL);
        db.execSQL("DROP TABLE IF EXISTS " + CANE_TABLE_SHORT);
        onCreate(db);
    }

    public void resetTable() { //Tested and functional
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DROP TABLE IF EXISTS " + CANE_TABLE_ANALYTICS);
        /*db.execSQL("CREATE TABLE IF NOT EXISTS " + CANE_TABLE_ANALYTICS + "(time long primary key, forceMax double, rollMean double, forceVariance double," +
                "pitchVariance double, rollVariance double)");*/
        db.execSQL("DROP TABLE IF EXISTS " + CANE_TABLE_FULL);
        /*db.execSQL("CREATE TABLE IF NOT EXISTS " + CANE_TABLE_FULL + "(time long primary key, force double, acc_x double, acc_y double, acc_z double," +
                "gyro_x double, gyro_y double, gyro_z double, pitch double, roll double)");*/
        db.execSQL("DROP TABLE IF EXISTS " + CANE_TABLE_SHORT);
        onCreate(db);
    }

    public static void deleteRows(long starttime, long endtime){
        SQLiteDatabase db = sInstance.getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM " + CANE_TABLE_FULL + " WHERE time >= " + Long.toString(starttime) +
                    " AND time <= " + Long.toString(endtime));

            db.setTransactionSuccessful();
        }catch(Exception e){
            Log.d(TAG,e.getMessage());
        }finally{
            db.endTransaction();
        }
    }

    public int getFullTableCount() //Tested and functional, but not tested to limits of full DB
    {
        int rowNum = 0;
        SQLiteDatabase db = this.getReadableDatabase();

        synchronized (this) {
            db.beginTransaction();
            Cursor res = null;
            try {

                res = db.rawQuery("select COUNT(time) from " + CANE_TABLE_FULL, null);
                res.moveToFirst();
                rowNum = res.getInt(0);
                db.setTransactionSuccessful();
            } catch (Exception e) {
            } finally {
                if (res != null) {
                    res.close();
                }
                db.endTransaction();
                //db.close();
                return rowNum;
            }
        }
    }
    //bulk insert raw data
    public static String fullInsertLoop(KinematicData[] caneArr) { //Inserts values for all available columns
        SQLiteDatabase db = sInstance.getWritableDatabase();
        String error = "Clear";
        int failure = 0;
        db.beginTransaction();
        try {
            for (int i = 0; i < caneArr.length; i++) {
                if (caneArr[i] == null) {
                    continue;
                }
                //int idIn = (int) (caneArr[i].time % 1000000000L);
                ContentValues values = new ContentValues();
                values.put(CANE_COLUMN_TIME, caneArr[i].time);
                values.put(CANE_COLUMN_FORCE, caneArr[i].force);
                values.put(CANE_COLUMN_ACC_X, caneArr[i].accx);
                values.put(CANE_COLUMN_ACC_Y, caneArr[i].accy);
                values.put(CANE_COLUMN_ACC_Z, caneArr[i].accz);
                values.put(CANE_COLUMN_GYRO_X, caneArr[i].gyrox);
                values.put(CANE_COLUMN_GYRO_Y, caneArr[i].gyroy);
                values.put(CANE_COLUMN_GYRO_Z, caneArr[i].gyroz);
                values.put(CANE_COLUMN_ANGLE_PITCH, caneArr[i].pitch);
                values.put(CANE_COLUMN_ANGLE_ROLL, caneArr[i].roll);

                db.insertOrThrow(CANE_TABLE_FULL, null, values);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            //db.endTransaction();
            failure = 1;
            error = e.getMessage();
        } finally {
            db.endTransaction();
           //db.close();
        }
        return error;
    }
    //Pull chunks of raw data
    public KinematicData[] getFullHour(long hour, long interval) { //Tested and functional
        long min = hour;
        long max = hour + interval;
        SQLiteDatabase db = this.getReadableDatabase();
        KinematicData[] caneArr = null;
        synchronized (this) {
            db.beginTransaction();
            Cursor res = null;
            try {
                res = db.rawQuery("select * from " + CANE_TABLE_FULL + " WHERE time >= " + Long.toString(min)
                        + " AND time < " + Long.toString(max), null);
                res.moveToFirst();
                caneArr = new KinematicData[res.getCount()];
                int rowNum = 0;

                while (res.isAfterLast() == false) {
                    long time = Long.parseLong(res.getString(res.getColumnIndex(CANE_COLUMN_TIME)));
                    double force = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_FORCE)));
                    double accx = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ACC_X)));
                    double accy = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ACC_Y)));
                    double accz = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ACC_Z)));
                    double gyrox = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_GYRO_X)));
                    double gyroy = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_GYRO_Y)));
                    double gyroz = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_GYRO_Z)));
                    double pitch = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ANGLE_PITCH)));
                    double roll = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ANGLE_ROLL)));
                    caneArr[rowNum] = new KinematicData(time, accx, accy, accz, gyrox, gyroy, gyroz, pitch, roll, force);
                    rowNum++;
                    res.moveToNext();
                }
                db.setTransactionSuccessful();
            } catch (Exception e) {
            } finally {
                if (res != null) {
                    res.close();
                }
                db.endTransaction();
                return caneArr;
            }
        }
    }

    public long[] getMinAndMaxForce() {
        SQLiteDatabase db = this.getReadableDatabase();
        long timeMin = -1;
        long timeMax = -1;

        synchronized (this) {
            db.beginTransaction();
            Cursor res = null;

            try {
                res = db.rawQuery("select MIN(force) from " + CANE_TABLE_FULL, null);
                res.moveToFirst();
                while (res.isAfterLast() == false) {
                    String testMin = res.getString(0);
                    timeMin = res.getLong(0);
                    res.moveToNext();
                }
                res = db.rawQuery("select MAX(force) from " + CANE_TABLE_FULL, null);
                res.moveToFirst();
                while (res.isAfterLast() == false) {
                    timeMax = res.getLong(0);
                    res.moveToNext();
                }
                db.setTransactionSuccessful();

            } catch (Exception e) {
                int failure = 1;
                String error = e.getMessage();
            } finally {
                db.endTransaction();
                if (res != null) {
                    res.close();
                }
            }
            return new long[]{timeMin, timeMax};
        }
    }

    //Returns the minimum and maximum time values stored in the raw DB
    public long[] getMinAndMaxTime() {
        SQLiteDatabase db = this.getReadableDatabase();
        long timeMin = -1;
        long timeMax = -1;
        Cursor res = null;

        db.beginTransaction();
        try {
            res = db.rawQuery("select MIN(time) from " + CANE_TABLE_FULL, null);
            res.moveToFirst();
            while (res.isAfterLast() == false) {
                String testMin = res.getString(0);
                timeMin = res.getLong(0);
                res.moveToNext();
                //timeMin = Long.parseLong(res.getString(res.getColumnIndex("MIN(time)")));
            }
            res.close();
            res = db.rawQuery("select MAX(time) from " + CANE_TABLE_FULL, null);
            res.moveToFirst();
            while (res.isAfterLast() == false) {
                timeMax = res.getLong(0);
                res.moveToNext();
            }
            db.setTransactionSuccessful();

        } catch (Exception e) {
            int failure = 1;
            String error = e.getMessage();
            Log.d(TAG,error);

        } finally {
            db.endTransaction();
            if(res != null) {
                res.close();
            }
            //db.close();
        }
        return new long[]{timeMin, timeMax};
    }

    //Generalized method for pulling chunks of raw data
    //TODO: Address modulator
    public KinematicData[] getInterval(long windowMin, long intervalSize, long modulator) {
        Cursor res = null;
        KinematicData[] caneArr = null;
        SQLiteDatabase db = this.getReadableDatabase();
        try{
            res = db.rawQuery("select * from " + CANE_TABLE_FULL + " WHERE time >= " + Long.toString(windowMin)
                    + " AND time < " + Long.toString(windowMin + intervalSize) + " AND time % " + Long.toString(modulator)
                    + " = 0", null);
            res.moveToFirst();
            caneArr = new KinematicData[res.getCount()];
            int rowNum = 0;
            db.beginTransaction();
            long time;
            double force;
            double accx;
            double accy;
            double accz;
            double gyrox;
            double gyroy;
            double gyroz;
            double pitch;
            double roll;
            while (res.isAfterLast() == false) {
                time = Long.parseLong(res.getString(res.getColumnIndex(CANE_COLUMN_TIME)));
                force = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_FORCE)));
                accx = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ACC_X)));
                accy = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ACC_Y)));
                accz = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ACC_Z)));
                gyrox = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_GYRO_X)));
                gyroy = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_GYRO_Y)));
                gyroz = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_GYRO_Z)));
                pitch = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ANGLE_PITCH)));
                roll = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ANGLE_ROLL)));
                caneArr[rowNum] = new KinematicData(time, accx, accy, accz, gyrox, gyroy, gyroz, pitch, roll, force);
                rowNum++;
                res.moveToNext();
            }
        } catch (Exception e) {
            int failure = 1;
            String error = e.getMessage();
            Log.d(TAG, error);
        } finally {
            if(res != null){
                res.close();
            }
            db.endTransaction();
            //db.close();
        }
        return caneArr;
    }

    //TODO: Address modulator. Fix this mess
    public KinematicData[] plotUpdate(long min, long max, long modulator) {
        long maxPull = (max / modulator) * modulator + 10 * modulator;
        long minPull = (min / modulator) * modulator - 10 * modulator;

        SQLiteDatabase db = this.getReadableDatabase();

        try {
            long minOut = -1;
            long maxOut = -1;
            db.beginTransaction();

            synchronized (this) {
                //Get boundaries of DB call
                Cursor resMin = db.rawQuery("select MIN(time) from " + CANE_TABLE_FULL + " WHERE time >= " + Long.toString(minPull)
                        + " AND time <= " + Long.toString(maxPull), null);
                resMin.moveToFirst();
                while (resMin.isAfterLast() == false) {
                    String testMin = resMin.getString(0);
                    minOut = resMin.getLong(0);
                    resMin.moveToNext();
                }
                resMin.close();
                Cursor resMax = db.rawQuery("select MAX(time) from " + CANE_TABLE_FULL + " WHERE time >= " + Long.toString(minPull)
                        + " AND time <= " + Long.toString(maxPull), null);
                resMax.moveToFirst();

                while (resMax.isAfterLast() == false) {
                    String testMin = resMax.getString(0);
                    maxOut = resMax.getLong(0);
                    resMax.moveToNext();
                }
                resMax.close();
            }

            KinematicData[] caneArr = modVals(minOut,maxOut,modulator);
            db.setTransactionSuccessful();
            return caneArr;
        } catch (Exception e) {
            //e.getMessage();
            return null;
        }finally{
            db.endTransaction();
            //db.close();
        }
    }

    public KinematicData[] modVals(long minOut, long maxOut, long mod){
        SQLiteDatabase db = this.getReadableDatabase();
        long span = maxOut-minOut;
        int count = (int)(span/mod);
        if((span % mod) != 0){
            count++;
        }
        Queue<KinematicData> queue = new LinkedList<>();
        int listLength = 0;

        synchronized (this) {
            Cursor res = null;
            for (int i = 0; i < count; i++) {
                db.beginTransaction();
                try {
                    res = db.rawQuery("select * from " + CANE_TABLE_FULL + " WHERE time >= " + Long.toString(minOut + i * mod)
                            + " AND time < " + Long.toString(minOut + (i + 1) * mod), null);
                    res.moveToFirst();
                    if (res.isAfterLast()) {
                        continue;
                    }
                    String temper = res.toString();
                    int tempy = res.getColumnIndex(CANE_COLUMN_TIME);
                    long time = Long.parseLong(res.getString(res.getColumnIndex(CANE_COLUMN_TIME)));
                    double force = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_FORCE)));
                    double accx = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ACC_X)));
                    double accy = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ACC_Y)));
                    double accz = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ACC_Z)));
                    double gyrox = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_GYRO_X)));
                    double gyroy = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_GYRO_Y)));
                    double gyroz = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_GYRO_Z)));
                    double pitch = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ANGLE_PITCH)));
                    double roll = Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ANGLE_ROLL)));
                    queue.add(new KinematicData(time, accx, accy, accz, gyrox, gyroy, gyroz, pitch, roll, force));
                    listLength++;
                    db.setTransactionSuccessful();
                } catch (Exception e) {
                    Log.d(TAG, e.getMessage());
                    //caneArr[i] = new KinematicData(minOut+i*mod, 0,0,0,0,0,0,0,0,0); //null;
                } finally {
                    db.endTransaction();
                    if (res != null) {
                        res.close();
                    }
                }
            }
        }
        if(listLength > 0){
            KinematicData[] caneArr = new KinematicData[listLength];
            int ind = 0;
            for(KinematicData caner : queue){
                caneArr[ind] = caner;
                ind++;
            }
            return caneArr;
        }else{
            return null;
        }
    }

    public KinematicData[] averageVals(long minOut, long maxOut, long mod){
        SQLiteDatabase db = this.getReadableDatabase();

        long span = maxOut-minOut;
        int count = (int)(span/mod);
        if((span % mod) != 0){
            count++;
        }
        KinematicData[] caneArr = new KinematicData[count];

        synchronized (this) {
            Cursor res = null;
            for (int i = 0; i < count; i++) {
                db.beginTransaction();
                try {
                    int rowNum = 0;
                    res = db.rawQuery("select * from " + CANE_TABLE_FULL + " WHERE time >= " + Long.toString(minOut + i * mod)
                            + " AND time < " + Long.toString(minOut + (i + 1) * mod), null);
                    res.moveToFirst();
                    long time = Long.parseLong(res.getString(res.getColumnIndex(CANE_COLUMN_TIME)));
                    double force = 0;
                    double accx = 0;
                    double accy = 0;
                    double accz = 0;
                    double gyrox = 0;
                    double gyroy = 0;
                    double gyroz = 0;
                    double pitch = 0;
                    double roll = 0;
                    while (res.isAfterLast() == false) {
                        force += Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_FORCE)));
                        accx += Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ACC_X)));
                        accy += Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ACC_Y)));
                        accz += Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ACC_Z)));
                        gyrox += Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_GYRO_X)));
                        gyroy += Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_GYRO_Y)));
                        gyroz += Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_GYRO_Z)));
                        pitch += Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ANGLE_PITCH)));
                        roll += Double.parseDouble(res.getString(res.getColumnIndex(CANE_COLUMN_ANGLE_ROLL)));
                        //caneArr[rowNum] = new KinematicData(time, accx, accy, accz, gyrox, gyroy, gyroz, pitch, roll, force);
                        rowNum++;
                        res.moveToNext();
                    }
                    //res.close();
                    caneArr[i] = new KinematicData(time, accx / rowNum, accy / rowNum, accz / rowNum,
                            gyrox / rowNum, gyroy / rowNum, gyroz / rowNum, pitch / rowNum, roll / rowNum, force / rowNum);
                    db.setTransactionSuccessful();
                } catch (Exception e) {
                    Log.d(TAG, e.getMessage());
                    caneArr[i] = new KinematicData(minOut + i * mod, 0, 0, 0, 0, 0, 0, 0, 0, 0); //null;
                } finally {
                    if (res != null) {
                        res.close();
                    }
                    db.endTransaction();
                }
            }
        }
        return caneArr;
    }

    public KinematicData[] seriesStart(long minStart) {
        long[] bounds = getMinAndMaxTime();
        long max = bounds[1];
        long min = minStart;//bounds[1] - 500;     //Arbitrary initial spread
        return plotUpdate(min, max, 1);
    }

}