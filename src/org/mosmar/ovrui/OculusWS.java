package org.mosmar.ovrui;

import static com.oculusvr.capi.OvrLibrary.ovrTrackingCaps.ovrTrackingCap_MagYawCorrection;
import static com.oculusvr.capi.OvrLibrary.ovrTrackingCaps.ovrTrackingCap_Orientation;
import static com.oculusvr.capi.OvrLibrary.ovrTrackingCaps.ovrTrackingCap_Position;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jmol.viewer.ActionManager;
import org.jmol.viewer.TransformManager;
import org.jmol.viewer.Viewer;

import com.oculusvr.capi.Hmd;
import com.oculusvr.capi.OvrQuaternionf;
import com.oculusvr.capi.OvrVector3f;
import com.oculusvr.capi.TrackingState;

import javajs.util.M3;
import javajs.util.Quat;

/**
 * WebSocket server that provides tracking info Dependencies are: JOVR, GSON and
 * Java_WebSockets
 *
 * @author Lars Ivar Hatledal
 * Modified by Mostafa Abdelraouf
 * Modified by Chen Zhezheng
 */
public class OculusWS {

    public static final int MODE_MOUSE = 0;
    public static final int MODE_VR = 1;
    

    private static int mMode = MODE_VR;
    private static boolean mIsEnabled = false;

    private static int mSelectedAtom = -1;
    static M3 mOrientation = null;
    static OculusWS instance = null;

    protected OculusWS() {
        // Exists only to defeat instantiation.
    }

    public static OculusWS getInstance() {
        if (instance == null) {
            instance = new OculusWS();
        }
        return instance;
    }

    public static void toggleMode(){
        if(isMouseMode()){
            activateVRMode();
        } else {
            activateMouseMode();
        }
    }

    public static void centerOnAtom(int index){
        mSelectedAtom = index;
        /*If an atom is clicked and we are in VR mode
        * zoom to that atom at 8000 and hide it to
        * create the illusion that the observer is inside that
        * atom
        */
        String script = null;
    //        if(OculusWS.isVRMode()){
    //            script =
    //                "display (all);"
    //                + "zoomTo (atomindex=" + Integer.toString(index) + ") 8000;"
    //                + "hide (atomindex=" + Integer.toString(index) + ");";
    //
    //        } else {
    //            script =
    //                "display (all);"
    //                + "zoomTo (atomindex=" + Integer.toString(index) + ") 100;";
    //        }


            script =
        "zoomTo (atomindex=" + Integer.toString(index) + ") ;";

        instance.mViewer.script(script);

        instance.mViewer.setStatusAtomPicked(index, null, null);
    }
    

    public static boolean isMouseMode(){
        return mMode == MODE_MOUSE;
    }

    public static boolean isVRMode(){
        return mMode == MODE_VR;
    }

    public static void activateMouseMode() {
      /*
       * We show all atoms, recall that in OVR mode we hide the central atom, 
       * We'll want to show that back again, We zoom back to 100
       */
      
    //        String script =
    //            "display (all);"
    //            + "zoomTo (atomindex=" + Integer.toString(mSelectedAtom) + ") 100;"
    //            + "set picking center;";
        String script =
        "zoomTo (atomindex=" + mSelectedAtom + ") ;"
            + "set picking center;";
        instance.mViewer.script(script);
        //instance.mViewer.refresh(1,"");
        mMode = MODE_MOUSE;
    }

    public static void activateVRMode(){
      //No atoms selected? Select the first atom in the molecule
    String target;
    //        if(mSelectedAtom == -1){
    //          target= " { 0 0 0 }";
    //        }else{
    //          target ="(atomindex=" + mSelectedAtom + ")";
    //        }
    target= (mSelectedAtom == -1)? " { 0 0 0 }" : ("(atomindex=" + mSelectedAtom + ")");
        String script =
        "zoomTo " + target + ";"
            + "set picking center;";
        instance.mViewer.script(script);
        mMode = MODE_VR;
    }

    public static boolean isEnabled(){
        return mIsEnabled;
    }

//    private long id = 0;
//    private SensorData latestData = new SensorData(0, 0, 0, 0, 0, 0, 0, 0);
    private boolean run = true;
    TransformManager mTM;
    ActionManager mAM;
    private Viewer mViewer;

    public M3 getOrientation(){
        return mOrientation;
    }

    /**
     * Program starting point
     * 
     * @param v JMol Viewer Object
     * @param tm JMol Transform Manager Object
   * @param am 
     */
    public void init(Viewer v, TransformManager tm, ActionManager am) {

        mTM = tm;
        mAM = am;
        mViewer = v;
        new Thread() {
            @Override
            public void run() {
              //Hmd stands for Head Mounted Display, That is our oculus :)
                Hmd.initialize();

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }

                Hmd hmd = Hmd.create(0);

                if (hmd == null) {
                    org.jmol.util.Logger.error(
                            "Unable to initialize oculus, verify that the HMD is connected and the service is up");
                    throw new IllegalStateException("Unable to initialize HMD");
                }

                hmd.configureTracking(ovrTrackingCap_Orientation
                        | ovrTrackingCap_MagYawCorrection
                        | ovrTrackingCap_Position, 0);

                mIsEnabled = true;
                
                //The thread that gets data from the sensor
                Thread t1 = new Thread(new SensorFetcher(hmd));
                t1.start();

                try {
                    t1.join();
                } catch (InterruptedException ex) {
                    Logger.getLogger(OculusWS.class.getName()).log(Level.SEVERE, null, ex);
                }

                hmd.destroy();
                Hmd.shutdown();
            }
        }.start();
    }

    //This methods is called whenever a new molecule is loaded
    public void resetViewer(){
        /*
         * ENables steroscopic mode and sets the size of atoms to be 15% of vander walls width
         */
      //  instance.mViewer.runScript("wireframe 0.15;spacefill reset;spacefill 15%;");
        instance.mViewer.runScript("stereo 0");
        // TODO Nafisa to change here
        activateVRMode();
       // activateMouseMode();
    }

    private static class SensorFetcher implements Runnable {

      private final Hmd hmd;

      private float deltaPX;
      private float deltaPY;
      private float deltaPZ;
      private float lastPX;
      private float lastPY;
      private float lastPZ;

      public SensorFetcher(Hmd hmd) {
        this.hmd = hmd;
      }

      @Override
      public void run() {
        
        int originalPressedCount;
        
        while (instance.run) {
          //Ignore VR input if mouse mode is enabled
          //TODO: Stop the thread instead
          if(mMode == MODE_MOUSE){
            try {
              Thread.sleep(1);
            } catch (InterruptedException ex) {
              Logger.getLogger(OculusWS.class.getName()).log(Level.SEVERE, null, ex);
            }
            continue;
          }

          TrackingState sensorState = hmd.getSensorState(Hmd.getTimeInSeconds());

          OvrVector3f pos = sensorState.HeadPose.Pose.Position;
          OvrQuaternionf quat = sensorState.HeadPose.Pose.Orientation;

          /*
           * this is the proper way to obtain the head rotation (by multiplying w by -1
           * and changing/correcting the matrix formula)
           */
          Quat q = new Quat();
          q.q0 = -quat.w;
          q.q1 =  quat.x;
          q.q2 =  quat.y;
          q.q3 =  quat.z;

          deltaPX = pos.x - lastPX;
          deltaPY = pos.y - lastPY;
          deltaPZ = pos.z - lastPZ;

          lastPX = pos.x;
          lastPY = pos.y;
          lastPZ = pos.z;             

          //Rotation
          mOrientation = q.getMatrix();
          instance.mTM.setRotation(mOrientation);

          originalPressedCount = instance.mAM.pressedCount;
          //Translation (XY)
          instance.mAM.pressedCount = 2;
          instance.mAM.checkDragWheelAction(0x2211, 0, 0,  Math.round(-deltaPX*2000), Math.round(deltaPY*2000), 0, 1);

          //Translation (Z)
          instance.mAM.pressedCount = 1; 
          instance.mAM.checkDragWheelAction(0x2111, 0, 0, 0, Math.round(-deltaPZ*120000/instance.mTM.zmPctSet), System.currentTimeMillis(), 1); //zoomby
//          instance.mAM.checkDragWheelAction(0x0120, 0, 0, 0, Math.round(-deltaPZ*500), System.currentTimeMillis(), 3); //zoombyfactor
          instance.mAM.pressedCount=originalPressedCount;
          
          instance.mViewer.refresh(1 ,"");
//          instance.mAM.pressedCount = 0;

          try {
            Thread.sleep(1);
          } catch (InterruptedException ex) {
            Logger.getLogger(OculusWS.class.getName()).log(Level.SEVERE, null, ex);
          }
        }
      }
    }

    /**Not used currently. May be used someday
     * 
     */
    @SuppressWarnings("unused")
    private static class SensorData {

        private final long id;
        private final double px, py, pz, qx, qy, qz, qw;

        public SensorData(long id, double px, double py, double pz, double qx, double qy, double qz, double qw) {
            this.id = id;
            this.px = px;
            this.py = py;
            this.pz = pz;
            this.qx = qx;
            this.qy = qy;
            this.qz = qz;
            this.qw = qw;
        }

        public long getId() {
            return id;
        }

        public double[] asArray() {
            return new double[]{id, px, py, pz, qx, qy, qz, qw};
        }

        @Override
        public String toString() {
            return String.format("Position: %.3f  %.3f  %.3f | Quat:  %.3f  %.3f  %.3f  %.3f", px, py, pz, qx, qy, qz, qw);
        }
    }
}