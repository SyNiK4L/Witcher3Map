package info.coreyjones.TestApp;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.util.Date;


public class SettingsMenu extends ActionBarActivity implements CompoundButton.OnCheckedChangeListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    protected boolean wearableState = false;
    protected boolean fileState = false;
    static final int READ_BLOCK_SIZE = 100;
    protected GoogleApiClient mGoogleApiClient;
    protected Location mLastLocation;
    private Location mCurrentLocation;
    private AddressResultReceiver mResultReceiver;
    private String mLastUpdateTime;
    boolean mRequestingLocationUpdates = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_menu);

        //Create Text View with random text
        TextView myTextView = (TextView) findViewById(R.id.settings_text);
        myTextView.setText(Html.fromHtml(getString(R.string.settings_info)));
        myTextView.setMovementMethod(LinkMovementMethod.getInstance());

        //Restore edit text field
        String defaultValue = getString(R.string.edit_message_default);
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        String theRestoredMessage = sharedPref.getString("the_message", defaultValue);
        if (theRestoredMessage != defaultValue) {
            toastMessage(getString(R.string.textRestored));
        }
        EditText editTextField = (EditText) findViewById(R.id.messageText);
        editTextField.setText(theRestoredMessage);

        //Setup wearable switch
        Switch wearSwitch = (Switch) findViewById(R.id.sendWearable);
        wearableState = sharedPref.getBoolean("switch_state", false);
        wearSwitch.setChecked(wearableState);
        wearSwitch.setOnCheckedChangeListener(this);

        final CheckBox theCheckBox = (CheckBox) findViewById(R.id.checkBox);
        theCheckBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fileState = theCheckBox.isChecked();
            }
        });
        buildGoogleApiClient();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        Toast.makeText(this, "The Switch is " + (isChecked ? "on" : "off"),
                Toast.LENGTH_SHORT).show();
        if (isChecked) {
            wearableState = true;
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean("switch_state", wearableState);
            editor.apply();
        } else {
            wearableState = false;
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean("switch_state", wearableState);
            editor.apply();
        }
    }

    public void saveText(View view) {
        //Grab text from editText field
        EditText editText = (EditText) findViewById(R.id.messageText);
        String message = editText.getText().toString();

        //Save text to preferences
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("the_message", message);
        editor.apply();
        toastMessage(getString(R.string.text_saved));

        //If wearable switch state is set to true. Send to watch
        if (wearableState) {
            sendWearableNoti(getString(R.string.text_saved), message);
        }
        if (fileState) {
            saveToFile(message, "textToSave.txt");
            readFile("textToSave.txt");
        }

    }

    public void resetText(View view) {
        EditText editText = (EditText) findViewById(R.id.messageText);
        editText.setText(getString(R.string.edit_message_default));
    }

    protected void toastMessage(CharSequence text) {
        Toast toast = Toast.makeText(getBaseContext(), text, Toast.LENGTH_SHORT);
        toast.show();
    }

    protected void sendWearableNoti(String notiTitle, String notiContent) {
        int notificationId = 001;
        int eventId = 1;
        String EXTRA_EVENT_ID = "";

        Intent viewIntent = new Intent(this, SettingsMenu.class);

        viewIntent.putExtra(EXTRA_EVENT_ID, eventId);
        PendingIntent viewPendingIntent =
                PendingIntent.getActivity(this, 0, viewIntent, 0);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_event_black_12dp)
                        .setContentTitle(notiTitle)
                        .setContentText(notiContent)
                        .setContentIntent(viewPendingIntent);

        // Get an instance of the NotificationManager service
        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);

        // Build the notification and issues it with notification manager.
        notificationManager.notify(notificationId, notificationBuilder.build());
    }

    protected void saveToFile(String textToSave, String filename) {
        try {
            FileOutputStream fileout = openFileOutput(filename, MODE_PRIVATE);
            OutputStreamWriter outputWriter = new OutputStreamWriter(fileout);
            outputWriter.write(textToSave);
            outputWriter.close();

            //display file saved message
            Toast.makeText(getBaseContext(), getString(R.string.fileSaved),
                    Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void readFile(String filename) {
        try {
            FileInputStream fileIn = openFileInput(filename);
            InputStreamReader InputRead = new InputStreamReader(fileIn);

            char[] inputBuffer = new char[READ_BLOCK_SIZE];
            String s = "";
            int charRead;

            while ((charRead = InputRead.read(inputBuffer)) > 0) {
                // char to string conversion
                String readstring = String.copyValueOf(inputBuffer, 0, charRead);
                s += readstring;
            }
            InputRead.close();
            TextView tv = (TextView) findViewById(R.id.fileOutput);
            tv.setText(s); ////Set the text to text view.

        } catch (Exception e) {
            e.printStackTrace();
        }


    }


    protected synchronized void buildGoogleApiClient() {

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    protected LocationRequest createLocationRequest() {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return mLocationRequest;
    }

    protected void startLocationUpdates(LocationRequest mLocationRequest) {
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    protected void stopLocationUpdates() {
        if (mGoogleApiClient.isConnected() && mRequestingLocationUpdates) {
            LocationServices.FusedLocationApi.removeLocationUpdates(
                    mGoogleApiClient, this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mGoogleApiClient.isConnected() && !mRequestingLocationUpdates) {
            LocationRequest mLocationRequest = createLocationRequest();
            startLocationUpdates(mLocationRequest);
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        TextView connectionStatus = (TextView) findViewById(R.id.connectStatus);
        connectionStatus.setText("Connected");

        LocationRequest mLocationRequest = createLocationRequest();

        if (mRequestingLocationUpdates) {
            startLocationUpdates(mLocationRequest);
        }

        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        if (mLastLocation != null) {
            TextView latitude = (TextView) findViewById(R.id.lat);
            latitude.setText(String.valueOf(mLastLocation.getLatitude()));

            TextView longitude = (TextView) findViewById(R.id.lon);
            longitude.setText(String.valueOf(mLastLocation.getLongitude()));

            startIntentService();
        }

    }

    @Override
    public void onConnectionSuspended(int i) {
        TextView connectStatus = (TextView) findViewById(R.id.connectStatus);
        connectStatus.setText("Suspended Connection");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        TextView connectStatus = (TextView) findViewById(R.id.connectStatus);
        connectStatus.setText("Connect to lat/long Failed");
    }


    protected void startIntentService() {
        mResultReceiver = new AddressResultReceiver(null);
        Intent intent = new Intent(this, FetchAddressIntentService.class);
        intent.putExtra(Constants.RECEIVER, mResultReceiver);
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, mLastLocation);
        startService(intent);
    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = mLastLocation = location;
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        updateUI();
    }

    private void updateUI() {
        TextView longitude = (TextView) findViewById(R.id.lon);
        TextView latitude = (TextView) findViewById(R.id.lat);
        latitude.setText(String.valueOf(mCurrentLocation.getLatitude()));
        longitude.setText(String.valueOf(mCurrentLocation.getLongitude()));
        TextView mLastUpdateTimeTextView = (TextView) findViewById(R.id.updateTime);
        mLastUpdateTimeTextView.setText(mLastUpdateTime);
        startIntentService();
    }

    class AddressResultReceiver extends ResultReceiver {
        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        public String mAddressOutput;

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            // Display the address string
            // or an error message sent from the intent service.
            mAddressOutput = resultData.getString(Constants.RESULT_DATA_KEY);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView addr = (TextView) findViewById(R.id.geoaddress);
                    addr.setText(mAddressOutput);
                    sendWearableNoti("Your Location", mAddressOutput);
                }
            });
        }

    }
}
