package com.example.camconvertorapp;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
//import android.support.v4.app.ActivityCompat;
//import android.support.v4.content.ContextCompat;
//import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.example.camconvertorapp.cameraModule.CameraSource;
import com.example.camconvertorapp.cameraModule.CameraSourcePreview;
import com.example.camconvertorapp.cameraModule.GraphicOverlay;
import com.example.camconvertorapp.currencyModule.FixerApi;
import com.example.camconvertorapp.currencyModule.Response;
import com.example.camconvertorapp.textxRecognitionModule.TextRecognitionProcessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProviders;
import kotlin.Triple;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class cameraActivity extends AppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback{

    // Fields.
    private static final String TEXT_DETECTION = "Text Detection";
    private static final String TAG = "LivePreviewActivity";
    private static final int PERMISSION_REQUESTS = 1;

    private CameraSource cameraSource = null;
    private CameraSourcePreview preview;
    private GraphicOverlay graphicOverlay;

    private TextView fixerRate;
    private Response fixerResponse;

    private String baseCurrency = "ILS";
    private String targetCurrency = "ILS";
    private float conversionRate = 1.0f;

    private TextRecognitionProcessor textRecognitionProcessor;

    private FrequenciesViewModel viewModel;

    // MainActivity main OnCreate method.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_activity);

        // Set camera source and overlay.
        Log.d(TAG, "onCreate");
        preview = (CameraSourcePreview) findViewById(R.id.firePreview);
        if (preview == null) {
            Log.d(TAG, "Preview is null");
        }
        graphicOverlay = (GraphicOverlay) findViewById(R.id.fireFaceOverlay);
        if (graphicOverlay == null) {
            Log.d(TAG, "graphicOverlay is null");
        }
        viewModel = ViewModelProviders.of(this).get(FrequenciesViewModel.class);




        // Get currency rates.
        fixerRate = (TextView) findViewById(R.id.conversionRate);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://data.fixer.io/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        FixerApi fixerApi = retrofit.create(FixerApi.class);
        Call<Response> call = fixerApi.getResponse();
        call.enqueue(new Callback<Response>() {
            @Override
            public void onResponse(Call<Response> call, retrofit2.Response<Response> response) {
                if (!response.isSuccessful()) {
                    fixerRate.setText("Code: " + response.code());
                    return;
                }

                fixerResponse = response.body();
            }

            @Override
            public void onFailure(Call<Response> call, Throwable t) {
                Toast.makeText(getApplicationContext(),"Error getting currency rates from Fixer.io", Toast.LENGTH_SHORT).show();
                ((TextView) findViewById(R.id.entryText)).setText("Please enable network and restart the app:)");
                ((TextView) findViewById(R.id.conversionRate)).setText("");
            }
        });

        TextView frequenciesSelected = findViewById(R.id.baseCurrency);
        frequenciesSelected.setText(getAllTypesOrdered(viewModel.getAllTypesStored()));

        //todo add here a buttom for moving the user to settings activity if he wants to change freqeuncies !!!



//        // Create spinners for available currencies.
//        Spinner spinnerBase = (Spinner) findViewById(R.id.baseSpinner);
//        Spinner spinnerTarget = (Spinner) findViewById(R.id.targetSpinner);
//        // Create an ArrayAdapter using the string array and a default spinner layout
//        ArrayAdapter<CharSequence> adapterBase = ArrayAdapter.createFromResource(this,
//                R.array.planets_array, android.R.layout.simple_spinner_item);
//        ArrayAdapter<CharSequence> adapterTarget = ArrayAdapter.createFromResource(this,
//                R.array.planets_array, android.R.layout.simple_spinner_item);
//        // Specify the layout to use when the list of choices appears
//        adapterBase.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//        adapterTarget.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//        // Apply the adapter to the spinner
//        spinnerBase.setAdapter(adapterBase);
//        spinnerTarget.setAdapter(adapterTarget);
//        spinnerBase.setOnItemSelectedListener(this);
//        spinnerTarget.setOnItemSelectedListener(this);




        if (fixerResponse != null) {

            HashMap<String, Pair<String, String>> allTypes = viewModel.getAllTypesStored();
            String[] strs = (String[]) allTypes.keySet().toArray(new String[0]);

            baseCurrency = allTypes.get("Currency").first;
            targetCurrency = allTypes.get("Currency").second;
            conversionRate = fixerResponse.rates.getConversionRate(baseCurrency, targetCurrency);

            textRecognitionProcessor.setConversionRate(conversionRate);
            fixerRate.setText(String.valueOf(conversionRate));
        }







        // Check permissions and start camera.
        if (allPermissionsGranted()) {
            createCameraSource();
        } else {
            getRuntimePermissions();
        }

    }




    public StringBuilder getAllTypesOrdered(HashMap<String, Pair<String,String>> typesUpdated){
        Triple<String,String, String> str ;
        ArrayList<Triple<String,String,String>> list = new ArrayList<Triple<String, String, String>>();
        StringBuilder str2  = new StringBuilder();

        String[] strs = (String[]) typesUpdated.keySet().toArray(new String[0]);
        for(String type :  strs)
        {
            str2.append("\n").append("type: " + type + "\n")
                    .append("     -> source sign: " + typesUpdated.get(type).first )
                    .append("\n")
                    .append("     -> target sign: " +typesUpdated.get(type).second)
                    .append("\n");


            str =  new Triple<String, String, String>(type , typesUpdated.get(type).first , typesUpdated.get(type).second);
            list.add(str);


        }
//        return list;
        return str2;
    }

    // Camera
    private void createCameraSource() {
        // If there's no existing cameraSource, create one.
        if (cameraSource == null) {
            cameraSource = new CameraSource(this, graphicOverlay);
        }

        Log.i(TAG, "Using Text Detector Processor");
        textRecognitionProcessor = new TextRecognitionProcessor();
        cameraSource.setMachineLearningFrameProcessor(textRecognitionProcessor);
    }

    /**
     * Starts or restarts the camera source, if it exists. If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {
        if (cameraSource != null) {
            try {
                if (preview == null) {
                    Log.d(TAG, "resume: Preview is null");
                }
                if (graphicOverlay == null) {
                    Log.d(TAG, "resume: graphOverlay is null");
                }
                preview.start(cameraSource, graphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                cameraSource.release();
                cameraSource = null;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        startCameraSource();
    }

    /** Stops the camera. */
    @Override
    protected void onPause() {
        super.onPause();
        preview.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraSource != null) {
            cameraSource.release();
        }
    }


    // Permissions
    private String[] getRequiredPermissions() {
        try {
            PackageInfo info =
                    this.getPackageManager()
                            .getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                return false;
            }
        }
        return true;
    }

    private void getRuntimePermissions() {
        List<String> allNeededPermissions = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                allNeededPermissions.add(permission);
            }
        }

        if (!allNeededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this, allNeededPermissions.toArray(new String[0]), PERMISSION_REQUESTS);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        Log.i(TAG, "Permission granted!");
        if (allPermissionsGranted()) {
            createCameraSource();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private static boolean isPermissionGranted(Context context, String permission) {
        if (ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission granted: " + permission);
            return true;
        }
        Log.i(TAG, "Permission NOT granted: " + permission);
        return false;
    }

//    @Override
//    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//        // An item was selected. You can retrieve the selected item using
//        // parent.getItemAtPosition(pos)
//        if (fixerResponse != null) {
//            switch(parent.getId()) {
//                case R.id.baseSpinner:
//                    baseCurrency = parent.getItemAtPosition(position).toString();
//                    conversionRate = fixerResponse.rates.getConversionRate(baseCurrency, targetCurrency);
//                    break;
//                case R.id.targetSpinner:
//                    targetCurrency = parent.getItemAtPosition(position).toString();
//                    conversionRate = fixerResponse.rates.getConversionRate(baseCurrency, targetCurrency);
//                    break;
//            }
//
//            textRecognitionProcessor.setConversionRate(conversionRate);
//            fixerRate.setText(String.valueOf(conversionRate));
//        }
//    }
//
//    @Override
//    public void onNothingSelected(AdapterView<?> parent) {
//        // Do nothing.
//    }
}