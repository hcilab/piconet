package com.unb.hci.android_ble;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**Created by Ian Smith 06/08/16*/
public class DBExport extends IntentService {
    DBHelper dbHelper;
    private static final String ACTION_EXPORT_DB = "com.drsorders.logger.action.ExportDB";
    private static final String ACTION_IMPORT_CSV = "com.drsorders.logger.action.ImportCSV";

    private static final String EXTRA_PARAM1 = "com.drsorders.logger.extra.PARAM1";
    private static final String EXTRA_PARAM2 = "com.drsorders.logger.extra.PARAM2";
    private static final String TAG = "DBExport";

    public DBExport() {
        super("DBExport");
    }

    /**
     * Starts this service to perform action ExportDB with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionExportDB(Context context, String param1, String param2) {
        Intent intent = new Intent(context, DBExport.class);
        intent.setAction(ACTION_EXPORT_DB);
        intent.putExtra(EXTRA_PARAM1, param1);
        intent.putExtra(EXTRA_PARAM2, param2);
        context.startService(intent);
    }

    public static void startActionImportCSV(Context context, String param1) {
        Intent intent = new Intent(context, DBExport.class);
        intent.setAction(ACTION_IMPORT_CSV);
        intent.putExtra(EXTRA_PARAM1, param1);
        //intent.putExtra(EXTRA_PARAM2, param2);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_EXPORT_DB.equals(action)) {
                final String param1 = intent.getStringExtra(EXTRA_PARAM1);
                final String param2 = intent.getStringExtra(EXTRA_PARAM2);
                handleActionExportDB(param1, param2);
            } else if (ACTION_IMPORT_CSV.equals(action)) {
                final String param1 = intent.getStringExtra(EXTRA_PARAM1);
                //final String param2 = intent.getStringExtra(EXTRA_PARAM2);
                handleActionImportCSV(param1);
            }
        }
    }

    private void handleActionImportCSV(String param1) {
        String fileName = param1;
        String baseDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        final String filePath = baseDir+File.separator+"Download"+File.separator+fileName;
        File f = new File(filePath);
        Handler h = new Handler(DBExport.this.getMainLooper());

        try {
            if (f.exists() && !f.isDirectory()) {
                CSVReader reader = new CSVReader(new FileReader(filePath));
                String [] nextLine;
                int counter = 0;
                KinematicData[] caneArr = new KinematicData[1000];
                while ((nextLine = reader.readNext()) != null) {
                    // nextLine[] is an array of values from the line

                    try {
                        if (nextLine.length == 11) {
                            caneArr[counter] = new KinematicData(Long.parseLong(nextLine[9]), Double.parseDouble(nextLine[0]),
                                    Double.parseDouble(nextLine[1]), Double.parseDouble(nextLine[2]), Double.parseDouble(nextLine[3]),
                                    Double.parseDouble(nextLine[4]), Double.parseDouble(nextLine[5]), 0, 0, Double.parseDouble(nextLine[6]));
                            counter++;
                            if (counter >= 1000) {
                                String error = DBHelper.fullInsertLoop(caneArr);
                                counter = 0;
                                Log.d(TAG, error);
                            }
                            //Log.d(TAG, nextLine[8]);
                        }
                    }catch(Exception e){
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(DBExport.this,"Error occurred :(",Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
                DBHelper.fullInsertLoop(caneArr);
            } else {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(DBExport.this,"File didn't exist :(",Toast.LENGTH_LONG).show();
                    }
                });
            }
        }catch(Exception e){
            h.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(DBExport.this,"An exception occurred :(",Toast.LENGTH_LONG).show();
                }
            });
        }

    }

    /**
     * Handle action Export DB in the provided background thread with the provided
     * parameters.
     */
    private void handleActionExportDB(String param1, String param2) {
        long pullSize = Long.parseLong(param1);
        String storageState = Environment.getExternalStorageState();
        String baseDir = getExternalFilesDir(null).getAbsolutePath();
        /*String baseDir2 = getApplicationInfo().dataDir;
        String baseDir3 = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        String baseDir4 = getApplication().getApplicationContext().getFilesDir().getAbsolutePath();*/
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy_MM_dd_HH'mm'ss.SSS");//format of date for file name
        Date now = new Date();
        //String baseDir = "/storage/extSdCard/";
        String fileName = param2+"_"+sdfDate.format(now) + ".csv";
        String filePath = baseDir + File.separator + fileName;
        File f = new File(filePath);
        FileWriter mFileWriter;
        CSVWriter writer;
        KinematicData[] canePullArr = null;
        try {
            if (f.exists() && !f.isDirectory()) {
                mFileWriter = new FileWriter(filePath, true);
                writer = new CSVWriter(mFileWriter);
            } else {
                writer = new CSVWriter(new FileWriter(filePath));
            }

            dbHelper = DBHelper.getInstance(this);  //Grab an instance of the DB manager
            long[] dbBounds = dbHelper.getMinAndMaxTime();  //Grab the temporal boundaries of the DB
            long startHour = dbBounds[0] / pullSize * pullSize;
            long endHour = dbBounds[1] / pullSize * pullSize;
            long numEntries = dbHelper.getFullTableCount();
            String[] dataLine;
            for (long hour = startHour; hour <= endHour; hour+=pullSize) {
                canePullArr = dbHelper.getFullHour(hour, pullSize); //Retrieves pullSize number of datapoints
                if(canePullArr == null) {
                    continue;
                }
                Log.d(TAG, "handleActionExportDB: Array "+hour+" length" + canePullArr.length);
                for(int i=0; i<canePullArr.length; i++){    //Write each data point to a line of the CSV
                    dataLine = new String[]{Long.toString(canePullArr[i].time),Double.toString(canePullArr[i].accx),
                            Double.toString(canePullArr[i].accy), Double.toString(canePullArr[i].accz),
                            Double.toString(canePullArr[i].gyrox), Double.toString(canePullArr[i].gyroy),
                            Double.toString(canePullArr[i].gyroz), Double.toString(canePullArr[i].pitch),
                            Double.toString(canePullArr[i].roll), Double.toString(canePullArr[i].force)};
                    writer.writeNext(dataLine);
                }
            }
            writer.flush();
            writer.close();

            dbHelper.deleteRows(dbBounds[0],dbBounds[1]);

            Handler h = new Handler(DBExport.this.getMainLooper());
            h.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(DBExport.this,"Database Export Successful!",Toast.LENGTH_LONG).show();
                }
            });
        }catch(Exception e){
            e.getMessage();
            e.getStackTrace()[0].getLineNumber();
            Handler h = new Handler(DBExport.this.getMainLooper());
            h.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(DBExport.this,"Database Export Unsuccessful...",Toast.LENGTH_LONG).show();
                }
            });
        }
    }//End method

}
