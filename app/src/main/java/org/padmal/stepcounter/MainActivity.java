package org.padmal.stepcounter;

import android.Manifest;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.CellInfo;
import android.telephony.NeighboringCellInfo;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.vistrav.ask.Ask;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements SensorEventListener, StepListener {


    //private final String STEP_URL = "http://202.94.70.33/server_testing/insertIRPSMobileHeadingPlusSteps.php";
    private String STEP_URL = "";
    private String WIFI_URL = "";
    private String IMU_URL = "";
    private String CELL_URL = "";

    private TextView stepStat, mSteps, mDirection, mThreshold;
    private Button runStop;

    private SensorManager sensorManager;
    private Sensor GyroSensor, AccelSensor, MagenetSensor;

    private WifiManager wifiManager;

    private StepDetector simpleStepDetector;
    private DefaultRetryPolicy retryPolicy;
    private RequestQueue queue;

    private Logger logger;

    private ImageView compass;

    private float[] OriReading;
    private float[] AccReading;
    private float[] GyroReading;
    private float[] MagReading;
    private float[] mGravity, mGeomagnetic;

    private List<ScanResult> scanResults;

    private int wifiCount = 0, imuCount = 0, stepCount = 0, cellCount, steps = 0;
    private float heading = 0.0f;
    private int count = 0;
    private int NodeCount = 0;

    private boolean started = false;

    private Timer timer;

    private String IMEI;

    private TextView oriX, oriY, oriZ, accX, accY, accZ, gyrX, gyrY, gyrZ, magX, magY, magZ;
    private TextView wifiStat, imuStat, cellStat, mNodes, startTime, endTime;
    private Spinner ipAddresses;

    private String IP = "202.94.70.33";

    private TelephonyManager tm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Ask.on(this).forPermissions(Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE).go();

        IMEI = getUniqueID();

        logger = new Logger(getApplicationContext());

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        queue = Volley.newRequestQueue(this);
        retryPolicy = new DefaultRetryPolicy(10000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES - 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);

        compass = (ImageView) findViewById(R.id.compass);

        initiateViews();
        initiateVariables();
        setupSensors();
        setupWiFi();
        setupStepCounter();
        setupTimer();
    }

    public String getUniqueID() {
        String myAndroidDeviceId;
        tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (tm.getDeviceId() != null) {
            myAndroidDeviceId = tm.getDeviceId();
        } else {
            myAndroidDeviceId = Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        }
        return myAndroidDeviceId;
    }

    /**
     * Post data to server according to time interval gap
     */
    private void postAllData() {
        if (started) {
            if (count == 4) {
                count = 0;
                postWiFiData();
            }
            count++;
            postIMUData();
            postStepData();
            postCellData();
        }
    }

    private void setupWiFi() {
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(getApplicationContext(), "Enabling WIFI", Toast.LENGTH_SHORT).show();
            wifiManager.setWifiEnabled(true);
        }
    }

    private void setupTimer() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (started) {
                    postAllData();
                }
            }
        }, 1000, 1000);
    }

    private void setupSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        AccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        MagenetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        GyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorManager.registerListener(this, AccelSensor, SensorManager.SENSOR_DELAY_UI * 2);
        sensorManager.registerListener(this, MagenetSensor, SensorManager.SENSOR_DELAY_UI * 2);
        sensorManager.registerListener(this, GyroSensor, SensorManager.SENSOR_DELAY_UI * 2);
    }

    private void setupStepCounter() {
        simpleStepDetector = new StepDetector();
        simpleStepDetector.registerListener(this);
    }

    /**
     * Initialize Views used in the UI
     */
    private void initiateViews() {

        runStop = (Button) findViewById(R.id.run_stop);
        runStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CELL_URL = "http://" + IP + "/tango/insert_tango_cell_tower_scan.php";
                IMU_URL = "http://" + IP + "/tango/insert_tango_raw_imu.php";
                WIFI_URL = "http://" + IP + "/tango/insert_tango_wifi_scan.php";
                STEP_URL = "http://" + IP + "/server_testing/insertIRPSMobileHeadingPlusStepsWithTime.php";
                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss");
                String now = sdf.format(new Date());
                if (started) {
                    postBreakData("Stopped!", false);
                    endTime.setText(now);
                } else {
                    postBreakData("Initiated!", true);
                    startTime.setText(now);
                    endTime.setText("End Time");
                }
                postSetupData();
                started = !started;
                runStop.setText(started ? "STOP!" : "START");
            }
        });

        oriX = (TextView) findViewById(R.id.ori_x);
        oriY = (TextView) findViewById(R.id.ori_y);
        oriZ = (TextView) findViewById(R.id.ori_z);

        accX = (TextView) findViewById(R.id.acc_x);
        accY = (TextView) findViewById(R.id.acc_y);
        accZ = (TextView) findViewById(R.id.acc_z);

        gyrX = (TextView) findViewById(R.id.gyr_x);
        gyrY = (TextView) findViewById(R.id.gyr_y);
        gyrZ = (TextView) findViewById(R.id.gyr_z);

        magX = (TextView) findViewById(R.id.mag_x);
        magY = (TextView) findViewById(R.id.mag_y);
        magZ = (TextView) findViewById(R.id.mag_z);

        wifiStat = (TextView) findViewById(R.id.wifi_stat);
        imuStat = (TextView) findViewById(R.id.imu_stat);
        mSteps = (TextView) findViewById(R.id.step_count_textview);
        stepStat = (TextView) findViewById(R.id.step_stat);
        mNodes = (TextView) findViewById(R.id.nodes);

        endTime = (TextView) findViewById(R.id.end_time_stamp);
        startTime = (TextView) findViewById(R.id.start_time_stamp);

        mDirection = (TextView) findViewById(R.id.direction);
        mThreshold = (TextView) findViewById(R.id.threshold);
        mThreshold.setText(String.valueOf((int) StepDetector.STEP_THRESHOLD));
        mSteps = (TextView) findViewById(R.id.step_count_textview);
        stepStat = (TextView) findViewById(R.id.step_stat);
        cellStat = (TextView) findViewById(R.id.cell_stat);

        ipAddresses = (Spinner) findViewById(R.id.ip_address_spinner);
        final List<String> ipAddressList = new ArrayList<>();
        ipAddressList.add("192.168.1.5");
        ipAddressList.add("202.94.70.33");

        ArrayAdapter<String> ipAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, ipAddressList);
        ipAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ipAddresses.setAdapter(ipAdapter);

        ipAddresses.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                IP = ipAddressList.get(i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }

    /**
     * Initialize variables used in
     */
    private void initiateVariables() {

        AccReading = new float[3];
        MagReading = new float[3];
        OriReading = new float[3];
        GyroReading = new float[3];
        mGravity = new float[3];
        mGeomagnetic = new float[3];
    }

    private void postStepData() {
        String data = ("?data=100," + IMEI + "," + System.currentTimeMillis() + "," + stepCount + "," + (String.valueOf(heading)));
        try {
            StringRequest stringRequest = new StringRequest(Request.Method.GET, STEP_URL + data, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    boolean success = response.contains("Data successfully created");
                    String displayText = (success) ? "SS " + steps : "SF " + steps;
                    if (success) {
                        steps++;
                        //stepCount = 0;
                    }
                    stepStat.setText(displayText);
                    stepStat.setBackgroundColor(displayText.contains("SS") ? Color.GREEN : Color.RED);
                    mSteps.setText(String.valueOf(stepCount));
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    /**/
                }
            }
            );
            stringRequest.setRetryPolicy(retryPolicy);
            queue.add(stringRequest);
        } catch (Exception e) {
            /**/
        }
    }

    private void postSetupData() {
        String data = ("?data=999,BreakPoint," + 0 + "," + (String.valueOf(heading)));
        try {
            StringRequest stringRequest = new StringRequest(Request.Method.GET, STEP_URL + data, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    boolean success = response.contains("Data successfully created");
                    String displayText = (success) ? "SS " + steps : "SF " + steps;
                    if (success) {
                        steps++;
                        stepCount = 0;
                    }
                    stepStat.setText(displayText);
                    stepStat.setBackgroundColor(displayText.contains("SS") ? Color.GREEN : Color.RED);
                    mSteps.setText(String.valueOf(stepCount));
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    /**/
                }
            }
            );
            stringRequest.setRetryPolicy(retryPolicy);
            queue.add(stringRequest);
        } catch (Exception e) {
            /**/
        }
    }

    private void postBreakData(final String msg, final boolean state) {
        try {
            StringRequest stringRequest = new StringRequest(Request.Method.POST, WIFI_URL, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    /**/
                }
            }

            ) {
                @Override
                protected Map<String, String> getParams() throws AuthFailureError {
                    Map<String, String> body = new HashMap<>();
                    try {
                        wifiManager.startScan();
                        // User ID *****************************************************************
                        body.put("user_id", IMEI);
                        // Pose Data ***************************************************************
                        if (state) {
                            wifiCount = 0;
                            imuCount = 0;
                            body.put("pose", "1.1,1.1,1.1,1.1,1.1,1.1,1.1,1.1,1.1,1.1,1.1,1.1,1.1");
                        } else {
                            body.put("pose", "2.2,2.2,2.2,2.2,2.2,2.2,2.2,2.2,2.2,2.2,2.2,2.2,2.2");
                        }
                        // Tango Time **************************************************************
                        body.put("tango_time", String.valueOf(System.currentTimeMillis()));
                        // Point Cloud *************************************************************
                        body.put("wifi_scan", "999,DUMMY,999,000,DUMMY");
                        return body;
                    } catch (Exception e) {
                        return null;
                    }
                }
            };
            stringRequest.setRetryPolicy(retryPolicy);
            queue.add(stringRequest);
        } catch (Exception e) {
            /**/
        }
    }

    private void postWiFiData() {
        try {
            StringRequest stringRequest = new StringRequest(Request.Method.POST, WIFI_URL, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    boolean success = response.contains("Data successfully created");
                    String displayText = (success) ? "WS " + wifiCount : "WF " + wifiCount;
                    if (success) {
                        wifiCount++;
                    }
                    wifiStat.setText(displayText);
                    wifiStat.setBackgroundColor(displayText.contains("W") ? Color.GREEN : Color.RED);
                    mNodes.setText(String.valueOf(NodeCount));
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    /**/
                }
            }

            ) {
                @Override
                protected Map<String, String> getParams() throws AuthFailureError {
                    Map<String, String> body = new HashMap<>();
                    try {
                        wifiManager.startScan();
                        // User ID *****************************************************************
                        body.put("user_id", IMEI);
                        // Pose Data ***************************************************************
                        //String uniPose = Arrays.toString(completePose).replaceAll("\\s", "");
                        //body.put("pose", uniPose.substring(1, uniPose.length() - 1));
                        body.put("pose", "0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0");
                        // Tango Time **************************************************************
                        body.put("tango_time", String.valueOf(System.currentTimeMillis()));
                        // Point Cloud *************************************************************
                        scanResults = wifiManager.getScanResults();
                        int APs = scanResults.size();
                        NodeCount = APs;
                        StringBuilder wifiString = new StringBuilder();
                        wifiString.append(APs);
                        wifiString.append(",");
                        for (ScanResult result : scanResults) {
                            wifiString.append(result.SSID);
                            wifiString.append(",");
                            wifiString.append(result.frequency);
                            wifiString.append(",");
                            wifiString.append(result.level);
                            wifiString.append(",");
                            wifiString.append(result.BSSID);
                            wifiString.append(",");
                        }
                        wifiString.deleteCharAt(wifiString.length() - 1);
                        body.put("wifi_scan", wifiString.toString());
                        return body;
                    } catch (Exception e) {
                        return null;
                    }
                }
            };
            stringRequest.setRetryPolicy(retryPolicy);
            queue.add(stringRequest);
        } catch (Exception e) {
            /**/
        }
    }

    private void postIMUData() {
        try {
            StringRequest stringRequest = new StringRequest(Request.Method.POST, IMU_URL, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    boolean success = response.contains("Data successfully created");
                    String displayText = (success) ? "IS " + imuCount : "IF " + imuCount;
                    if (success) {
                        imuCount++;
                    }
                    imuStat.setText(displayText);
                    imuStat.setBackgroundColor(displayText.contains("S") ? Color.GREEN : Color.RED);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    /**/
                }
            }

            ) {
                @Override
                protected Map<String, String> getParams() throws AuthFailureError {
                    Map<String, String> body = new HashMap<>();
                    try {
                        // User ID *****************************************************************
                        body.put("user_id", IMEI);
                        // Pose Data ***************************************************************
                        //String uniPose = Arrays.toString(completePose).replaceAll("\\s", "");
                        //body.put("pose", uniPose.substring(1, uniPose.length() - 1));
                        body.put("pose", "0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0");
                        // Tango Time **************************************************************
                        body.put("tango_time", String.valueOf(System.currentTimeMillis()));
                        // Orientation *************************************************************
                        String Orientation = Arrays.toString(OriReading).replaceAll("\\s", "");
                        body.put("orientation", Orientation.substring(1, Orientation.length() - 1));
                        // Acceleration ************************************************************
                        String Acceleration = Arrays.toString(AccReading).replaceAll("\\s", "");
                        body.put("acceleration", Acceleration.substring(1, Acceleration.length() - 1));
                        // Gyroscope ***************************************************************
                        String Gyroscope = Arrays.toString(GyroReading).replaceAll("\\s", "");
                        body.put("gyroscope", Gyroscope.substring(1, Gyroscope.length() - 1));
                        // Magnetic ****************************************************************
                        String Magenetic = Arrays.toString(MagReading).replaceAll("\\s", "");
                        body.put("magnetic_field", Magenetic.substring(1, Magenetic.length() - 1));
                        return body;
                    } catch (Exception e) {
                        return null;
                    }
                }
            };
            stringRequest.setRetryPolicy(retryPolicy);
            queue.add(stringRequest);
        } catch (Exception e) {
            /**/
        }
    }

    private void postCellData() {
        try {
            StringRequest stringRequest = new StringRequest(Request.Method.POST, CELL_URL, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    boolean success = response.contains("Data successfully created");
                    String displayText = (success) ? "CS " + cellCount : "CF " + cellCount;
                    if (success) {
                        cellCount++;
                    }
                    cellStat.setText(displayText);
                    cellStat.setBackgroundColor(displayText.contains("S") ? Color.GREEN : Color.RED);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    /**/
                }
            }

            ) {
                @Override
                protected Map<String, String> getParams() throws AuthFailureError {
                    Map<String, String> body = new HashMap<>();
                    try {
                        // User ID *****************************************************************
                        body.put("user_id", IMEI);
                        // Pose Data ***************************************************************
                        //String uniPose = Arrays.toString(completePose).replaceAll("\\s", "");
                        //body.put("pose", uniPose.substring(1, uniPose.length() - 1));
                        body.put("pose", "0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0");
                        // Tango Time **************************************************************
                        body.put("tango_time", String.valueOf(System.currentTimeMillis()));
                        // Cell Scan *************************************************************
                        try {
                            StringBuilder cellString = new StringBuilder();
                            HashMap<String, String> cellIDwithPSC = logger.getCellIDs();
                            GsmCellLocation location = (GsmCellLocation) tm.getCellLocation();

                            final int cid = location.getCid();
                            final int lac = location.getLac();

                            final String networkOperator = tm.getNetworkOperator();
                            final int mcc = Integer.parseInt(networkOperator.substring(0, 3));
                            final int mnc = Integer.parseInt(networkOperator.substring(3));
                            int psc = Math.abs(location.getPsc());

                            // Update local store of PSCs with Cell IDs if a new CID is encountered
                            if (!cellIDwithPSC.containsKey(String.valueOf(psc))) {
                                logger.setCellIDs(String.valueOf(psc) + "," + String.valueOf(cid));
                            }
                            List<NeighboringCellInfo> list = tm.getNeighboringCellInfo();

                            if (list.size() > 0) {
                                cellString.append(list.size()).append(",").append(lac).append(",").append(mcc).append(",").append(mnc).append(",");
                                for (NeighboringCellInfo i : list) {
                                    if (cellIDwithPSC.containsKey(String.valueOf(Math.abs(i.getPsc())))) {
                                        cellString.append(cellIDwithPSC.get(String.valueOf(i.getPsc())))
                                                .append(",").append(i.getPsc()).append(",")
                                                .append(i.getRssi()).append(",");
                                    } else {
                                        cellString.append(-1)
                                                .append(",").append(i.getPsc()).append(",")
                                                .append(i.getRssi()).append(",");
                                    }
                                }
                                if (cellString.length() > 3) {
                                    cellString.deleteCharAt(cellString.length() - 1);
                                }
                                Log.d("Padmal", cellString.toString());
                                body.put("cell_tower_scan", cellString.toString());
                            } else {
                                throw new Exception();
                            }
                        } catch (Exception e) {
                            body.put("cell_tower_scan", "No cellular data available");
                        }
                        return body;
                    } catch (Exception e) {
                        return null;
                    }
                }
            };
            stringRequest.setRetryPolicy(retryPolicy);
            queue.add(stringRequest);
        } catch (Exception e) {
            /**/
        }
    }

    /**
     * Listeners for data collection ***************************************************************
     */
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor sensor = sensorEvent.sensor;

        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            simpleStepDetector.updateAccel(
                    sensorEvent.timestamp, sensorEvent.values[0], sensorEvent.values[1], sensorEvent.values[2]);
            AccReading[0] = sensorEvent.values[0];
            AccReading[1] = sensorEvent.values[1];
            AccReading[2] = sensorEvent.values[2];
            accX.setText(String.valueOf(AccReading[0]));
            accY.setText(String.valueOf(AccReading[1]));
            accZ.setText(String.valueOf(AccReading[2]));
            mGravity = sensorEvent.values;
        }
        if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            GyroReading[0] = sensorEvent.values[0];
            GyroReading[1] = sensorEvent.values[1];
            GyroReading[2] = sensorEvent.values[2];
            gyrX.setText(String.valueOf(GyroReading[0]));
            gyrY.setText(String.valueOf(GyroReading[1]));
            gyrZ.setText(String.valueOf(GyroReading[2]));
        }
        if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            MagReading[0] = sensorEvent.values[0];
            MagReading[1] = sensorEvent.values[1];
            MagReading[2] = sensorEvent.values[2];
            magX.setText(String.valueOf(MagReading[0]));
            magY.setText(String.valueOf(MagReading[1]));
            magZ.setText(String.valueOf(MagReading[2]));
            mGeomagnetic = sensorEvent.values;
        }
        float[] R = new float[9];
        float[] I = new float[9];
        boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
        if (success) {
            float[] orientationData = new float[3];
            SensorManager.getOrientation(R, orientationData);
            OriReading[0] = orientationData[0]; // Azimuth -- North = 0
            OriReading[1] = orientationData[1]; // Pitch
            OriReading[2] = orientationData[2]; // Roll
            oriX.setText(String.valueOf(OriReading[0]));
            oriY.setText(String.valueOf(OriReading[1]));
            oriZ.setText(String.valueOf(OriReading[2]));
            // Calculate degrees
            float angle = (float) (OriReading[0] * (180 / Math.PI));
            if (angle < 0) {
                angle = angle + 360;
            }
            mDirection.setText(String.valueOf(angle));
            compass.setRotation(angle);
            heading = angle;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {/**/}

    @Override
    public void stepMade(long timeNS) {
        mSteps.setText(String.valueOf(stepCount));
        stepCount++;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void increase(View view) {
        StepDetector.STEP_THRESHOLD++;
        mThreshold.setText(String.valueOf((int) StepDetector.STEP_THRESHOLD));
    }

    public void decrease(View view) {
        StepDetector.STEP_THRESHOLD--;
        mThreshold.setText(String.valueOf((int) StepDetector.STEP_THRESHOLD));
    }
}
