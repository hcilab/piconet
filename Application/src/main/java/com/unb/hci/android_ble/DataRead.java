package com.unb.hci.android_ble;

import android.util.Log;

import java.nio.ByteBuffer;

/**
 * Created by Ian on 2016-06-24.
 */
public class DataRead {

    private final static String TAG = DataRead.class.getSimpleName();

    public final char plusSign = '+';
    public static final char minusSign = '-';
    public static final int objLength = 16; //18    //TODO: Generalize this for any kinematic data model

    public static KinematicData translateData(byte[] input, long timeIn){
        if(input.length < objLength){
            Log.d(TAG, "translateData: data too short");
            return null;
        }else if(input.length > objLength){
            Log.d(TAG, "translateData: data too long");
        }
        double[] doubleArr = new double[objLength/2];
        byte[] temp;
        ByteBuffer buff;
        for(int i= 0; i < objLength; i = i+2){
            temp = new byte[2];
            temp[1] = input[i];
            temp[0] = input[i+1];
            buff = ByteBuffer.wrap(temp);
            doubleArr[i/2] = (double) buff.getShort();
        }
        //return new KinematicData(timeIn, doubleArr[3], doubleArr[4],doubleArr[5],doubleArr[0],doubleArr[1],doubleArr[2],0,0,doubleArr[6]);
        // time, accX, accY, accZ, gyroX, gyroY, gyroZ, Dist, FSR1, FSR2
        return new KinematicData(timeIn, doubleArr[3], doubleArr[4],doubleArr[5],doubleArr[0],doubleArr[1],doubleArr[2],doubleArr[6],doubleArr[7],0);//,doubleArr[8]);
    }
}
