package com.unb.hci.android_ble;

/**
 * Created by Ian on 2016-05-10.
 */
public class KinematicData {
    public long time;
    public double force;
    public double accx;
    public double accy;
    public double accz;
    public double gyrox;
    public double gyroy;
    public double gyroz;
    public double pitch;
    public double roll;

    public KinematicData(long timeIn, double accxIn, double accyIn, double acczIn, double gyroxIn, double gyroyIn,
                         double gyrozIn, double pitchIn, double rollIn, double forceIn) {
        time = timeIn;
        force = forceIn;
        accx = accxIn;
        accy = accyIn;
        accz = acczIn;
        gyrox = gyroxIn;
        gyroy = gyroyIn;
        gyroz = gyrozIn;
        pitch = pitchIn;
        roll = rollIn;
    }

    public String toString(){
        String out = Long.toString(time)+" "+ Double.toString(force)+" "+ Double.toString(accx)+
                " "+ Double.toString(accy)+" "+ Double.toString(accz)+" "+ Double.toString(gyrox)+
                " "+ Double.toString(gyroy)+" "+ Double.toString(gyroz)+" "+ Double.toString(pitch)+
                " "+ Double.toString(roll)+" " ;
        return out;
    }
}
