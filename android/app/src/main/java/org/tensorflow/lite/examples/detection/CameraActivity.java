/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.detection;

import android.Manifest;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import androidx.annotation.NonNull;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.nio.ByteBuffer;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Classifier.Recognition;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.speech.tts.TextToSpeech;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;


public abstract class CameraActivity extends AppCompatActivity
    implements OnImageAvailableListener,
        Camera.PreviewCallback,
        CompoundButton.OnCheckedChangeListener,
        View.OnClickListener {
  private static final Logger LOGGER = new Logger();

  private static final int PERMISSIONS_REQUEST = 1;

  private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
  protected int previewWidth = 0;
  protected int previewHeight = 0;
  private boolean debug = false;
  private Handler handler;
  private HandlerThread handlerThread;
  private boolean useCamera2API;
  private boolean isProcessingFrame = false;
  private byte[][] yuvBytes = new byte[3][];
  private int[] rgbBytes = null;
  private int yRowStride;
  private Runnable postInferenceCallback;
  private Runnable imageConverter;

  private LinearLayout bottomSheetLayout;
  private LinearLayout gestureLayout;
  private BottomSheetBehavior sheetBehavior;

  protected TextView frameValueTextView, cropValueTextView, inferenceTimeTextView;
  protected ImageView bottomSheetArrowImageView;
  private ImageView plusImageView, minusImageView;
  private SwitchCompat apiSwitchCompat;
  private TextView threadsTextView;

  TextToSpeech t1;

  // GUI Components
  private TextView mBluetoothStatus;
  private TextView mReadBuffer = null;
  private TextView recognitionTextView;
  private Button mScanBtn;
  private Button mOffBtn;
  private Button mListPairedDevicesBtn;
  private Button mDiscoverBtn;
  private BluetoothAdapter mBTAdapter;
  private Set<BluetoothDevice> mPairedDevices;
  private ArrayAdapter<String> mBTArrayAdapter;
  private ListView mDevicesListView;
  private CheckBox mLED1;

  private final String TAG = CameraActivity.class.getSimpleName();
  private Handler mHandler; // Our main handler that will receive callback notifications
  private ConnectedThread mConnectedThread; // bluetooth background worker thread to send and receive data
  private BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path

  private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier


  // #defines for identifying shared types between calling functions
  private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
  private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
  private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status


  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    LOGGER.d("onCreate " + this);
    super.onCreate(null);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    setContentView(R.layout.activity_camera);
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayShowTitleEnabled(false);

    if (hasPermission()) {
      setFragment();
    } else {
      requestPermission();
    }

    threadsTextView = findViewById(R.id.threads);
    plusImageView = findViewById(R.id.plus);
    minusImageView = findViewById(R.id.minus);
    apiSwitchCompat = findViewById(R.id.api_info_switch);
    bottomSheetLayout = findViewById(R.id.bottom_sheet_layout);
    gestureLayout = findViewById(R.id.gesture_layout);
    sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
    bottomSheetArrowImageView = findViewById(R.id.bottom_sheet_arrow);
    mBluetoothStatus = (TextView) findViewById(R.id.bluetoothStatus);
    mReadBuffer = (TextView) findViewById(R.id.readBuffer);
    mScanBtn = (Button) findViewById(R.id.scan);
    mOffBtn = (Button) findViewById(R.id.off);
    mDiscoverBtn = (Button) findViewById(R.id.discover);
    mListPairedDevicesBtn = (Button) findViewById(R.id.PairedBtn);
    mLED1 = (CheckBox) findViewById(R.id.checkboxLED1);
    recognitionTextView = findViewById(R.id.recognitionTextView);

    mBTArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
    mBTAdapter = BluetoothAdapter.getDefaultAdapter(); // get a handle on the bluetooth radio

    mDevicesListView = (ListView) findViewById(R.id.devicesListView);
    mDevicesListView.setAdapter(mBTArrayAdapter); // assign model to view
    //mDevicesListView.setOnItemClickListener(mDeviceClickListener);

    t1 = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
      @Override
      public void onInit(int status) {
        if (status != TextToSpeech.ERROR) {
          t1.setLanguage(Locale.UK);
        }
      }
    });

    ViewTreeObserver vto = gestureLayout.getViewTreeObserver();
    vto.addOnGlobalLayoutListener(
            new ViewTreeObserver.OnGlobalLayoutListener() {
              @Override
              public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                  gestureLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                } else {
                  gestureLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
                //                int width = bottomSheetLayout.getMeasuredWidth();
                int height = gestureLayout.getMeasuredHeight();

                sheetBehavior.setPeekHeight(height);
              }
            });
    sheetBehavior.setHideable(false);

    sheetBehavior.setBottomSheetCallback(
            new BottomSheetBehavior.BottomSheetCallback() {
              @Override
              public void onStateChanged(@NonNull View bottomSheet, int newState) {
                switch (newState) {
                  case BottomSheetBehavior.STATE_HIDDEN:
                    break;
                  case BottomSheetBehavior.STATE_EXPANDED: {
                    bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_down);
                  }
                  break;
                  case BottomSheetBehavior.STATE_COLLAPSED: {
                    bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
                  }
                  break;
                  case BottomSheetBehavior.STATE_DRAGGING:
                    break;
                  case BottomSheetBehavior.STATE_SETTLING:
                    bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
                    break;
                }
              }

              @Override
              public void onSlide(@NonNull View bottomSheet, float slideOffset) {
              }
            });

    frameValueTextView = findViewById(R.id.frame_info);
    cropValueTextView = findViewById(R.id.crop_info);
    inferenceTimeTextView = findViewById(R.id.inference_info);

    apiSwitchCompat.setOnCheckedChangeListener(this);

    plusImageView.setOnClickListener(this);
    minusImageView.setOnClickListener(this);

    // Ask for location permission if not already allowed
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);


    mHandler = new Handler() {
      public void handleMessage(android.os.Message msg) {
        if (msg.what == MESSAGE_READ) {
          String readMessage = null;
          try {
            readMessage = new String((byte[]) msg.obj, "UTF-8");
            mReadBuffer.setText(readMessage);
          } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
          }
          //String toSpeak = mReadBuffer.getText().toString();
          //Toast.makeText(getApplicationContext(), toSpeak,Toast.LENGTH_SHORT).show();
          //if(!t1.isSpeaking())t1.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
        }
        else mReadBuffer.setText("");

        if (msg.what == CONNECTING_STATUS) {
          if (msg.arg1 == 1)
            mBluetoothStatus.setText("Connected to Device: " + (String) (msg.obj));
          else{
            mBluetoothStatus.setText("Connection Failed");
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
              @Override
              public void run() {
                mBluetoothStatus.setText("Retrying");
                listPairedDevices();
              }
            }, 1000);

          }

        }
      }
    };

    if (mBTArrayAdapter == null) {
      // Device does not support Bluetooth
      mBluetoothStatus.setText("Status: Bluetooth not found");
      Toast.makeText(getApplicationContext(), "Bluetooth device not found!", Toast.LENGTH_SHORT).show();
    } else {

      mLED1.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          if (mConnectedThread != null) //First check to make sure thread created
            mConnectedThread.write("1");
        }
      });

      bluetoothOn();
/*
            mScanBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                }
            });*/

      mOffBtn.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          bluetoothOff(v);
        }
      });

      listPairedDevices();

            /*mListPairedDevicesBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v){
                    listPairedDevices(v);
                }
            });*/

      mDiscoverBtn.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          discover(v);
        }
      });
    }
  }

  private void bluetoothOn(){
            /*Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);*/
    mBTAdapter.enable();
    if (!mBTAdapter.isEnabled()) {
      mBluetoothStatus.setText("Bluetooth enabled");
      Toast.makeText(getApplicationContext(),"Bluetooth turned on",Toast.LENGTH_SHORT).show();

    }
    else{
      //Toast.makeText(getApplicationContext(),"Bluetooth is already on", Toast.LENGTH_SHORT).show();
    }
  }

  // Enter here after user selects "yes" or "no" to enabling radio
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent Data){
    // Check which request we're responding to
    if (requestCode == REQUEST_ENABLE_BT) {
      // Make sure the request was successful
      if (resultCode == RESULT_OK) {
        // The user picked a contact.
        // The Intent's data Uri identifies which contact was selected.
        mBluetoothStatus.setText("Enabled");
      }
      else
        mBluetoothStatus.setText("Disabled");
    }
  }

  private void bluetoothOff(View view){
    mBTAdapter.disable(); // turn off
    mBluetoothStatus.setText("Bluetooth disabled");
    Toast.makeText(getApplicationContext(),"Bluetooth turned Off", Toast.LENGTH_SHORT).show();
  }

  private void discover(View view){
    // Check if the device is already discovering
    if(mBTAdapter.isDiscovering()){
      mBTAdapter.cancelDiscovery();
      Toast.makeText(getApplicationContext(),"Discovery stopped",Toast.LENGTH_SHORT).show();
    }
    else{
      if(mBTAdapter.isEnabled()) {
        mBTArrayAdapter.clear(); // clear items
        mBTAdapter.startDiscovery();
        Toast.makeText(getApplicationContext(), "Discovery started", Toast.LENGTH_SHORT).show();
        registerReceiver(blReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
      }
      else{
        Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
      }
    }
  }

  final BroadcastReceiver blReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if(BluetoothDevice.ACTION_FOUND.equals(action)){
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        // add the name to the list
        mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
        mBTArrayAdapter.notifyDataSetChanged();
      }
    }
  };

  private void listPairedDevices(){
    mBTArrayAdapter.clear();
    mPairedDevices = mBTAdapter.getBondedDevices();
    if(mBTAdapter.isEnabled()) {
      // put it's one to the adapter
      for (BluetoothDevice device : mPairedDevices)
        mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());

      //Toast.makeText(getApplicationContext(), "Show Paired Devices", Toast.LENGTH_SHORT).show();
    }
    else
      Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();

    new Thread()
    {
      public void run() {
        boolean fail = false;

        BluetoothDevice device = mBTAdapter.getRemoteDevice(address);

        try {
          mBTSocket = createBluetoothSocket(device);
        } catch (IOException e) {
          fail = true;
          Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
        }
        // Establish the Bluetooth socket connection.
        try {
          mBTSocket.connect();
        } catch (IOException e) {
          try {
            fail = true;
            mBTSocket.close();
            mHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                    .sendToTarget();
          } catch (IOException e2) {
            //insert code to deal with this
            Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
          }
        }
        if(fail == false) {
          mConnectedThread = new ConnectedThread(mBTSocket);
          mConnectedThread.start();

          mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                  .sendToTarget();
        }
      }
    }.start();


  }
  /*
      private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
          public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {

              if(!mBTAdapter.isEnabled()) {
                  Toast.makeText(getBaseContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
                  return;
              }

              mBluetoothStatus.setText("Connecting...");
              // Get the device MAC address, which is the last 17 chars in the View
              String info = ((TextView) v).getText().toString();*/
  final String address = "00:18:91:D6:AA:B5";
  final String name = "TN200";

  // Spawn a new thread to avoid blocking the GUI one

  //}
  //};

  private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
    try {
      final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
      return (BluetoothSocket) m.invoke(device, BTMODULEUUID);
    } catch (Exception e) {
      Log.e(TAG, "Could not create Insecure RFComm Connection",e);
    }
    return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
  }

  private class ConnectedThread extends Thread {
    private final BluetoothSocket mmSocket;
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;

    public ConnectedThread(BluetoothSocket socket) {
      mmSocket = socket;
      InputStream tmpIn = null;
      OutputStream tmpOut = null;

      // Get the input and output streams, using temp objects because
      // member streams are final
      try {
        tmpIn = socket.getInputStream();
        tmpOut = socket.getOutputStream();
      } catch (IOException e) { }

      mmInStream = tmpIn;
      mmOutStream = tmpOut;
    }

    public void run() {
      byte[] buffer = new byte[1024];  // buffer store for the stream
      int bytes; // bytes returned from read()
      // Keep listening to the InputStream until an exception occurs
      while (true) {
        try {
          // Read from the InputStream
          bytes = mmInStream.available();
          if(bytes != 0) {
            buffer = new byte[1024];
            SystemClock.sleep(100); //pause and wait for rest of data. Adjust this depending on your sending speed.
            bytes = mmInStream.available(); // how many bytes are ready to be read?
            bytes = mmInStream.read(buffer, 0, bytes); // record how many bytes we actually read
            mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                    .sendToTarget(); // Send the obtained bytes to the UI activity
          }
        } catch (IOException e) {
          e.printStackTrace();

          break;
        }
      }
    }

    /* Call this from the main activity to send data to the remote device */
    public void write(String input) {
      byte[] bytes = input.getBytes();           //converts entered String into bytes
      try {
        mmOutStream.write(bytes);
      } catch (IOException e) { }
    }

    /* Call this from the main activity to shutdown the connection */
    public void cancel() {
      try {
        mmSocket.close();
      } catch (IOException e) { }
    }
  }

  protected int[] getRgbBytes() {
    imageConverter.run();
    return rgbBytes;
  }

  protected int getLuminanceStride() {
    return yRowStride;
  }

  protected byte[] getLuminance() {
    return yuvBytes[0];
  }

  /** Callback for android.hardware.Camera API */
  @Override
  public void onPreviewFrame(final byte[] bytes, final Camera camera) {
    if (isProcessingFrame) {
      LOGGER.w("Dropping frame!");
      return;
    }

    try {
      // Initialize the storage bitmaps once when the resolution is known.
      if (rgbBytes == null) {
        Camera.Size previewSize = camera.getParameters().getPreviewSize();
        previewHeight = previewSize.height;
        previewWidth = previewSize.width;
        rgbBytes = new int[previewWidth * previewHeight];
        onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), 90);
      }
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      return;
    }

    isProcessingFrame = true;
    yuvBytes[0] = bytes;
    yRowStride = previewWidth;

    imageConverter =
        new Runnable() {
          @Override
          public void run() {
            ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);
          }
        };

    postInferenceCallback =
        new Runnable() {
          @Override
          public void run() {
            camera.addCallbackBuffer(bytes);
            isProcessingFrame = false;
          }
        };
    processImage();
  }

  /** Callback for Camera2 API */
  @Override
  public void onImageAvailable(final ImageReader reader) {
    // We need wait until we have some size from onPreviewSizeChosen
    if (previewWidth == 0 || previewHeight == 0) {
      return;
    }
    if (rgbBytes == null) {
      rgbBytes = new int[previewWidth * previewHeight];
    }
    try {
      final Image image = reader.acquireLatestImage();

      if (image == null) {
        return;
      }

      if (isProcessingFrame) {
        image.close();
        return;
      }
      isProcessingFrame = true;
      Trace.beginSection("imageAvailable");
      final Plane[] planes = image.getPlanes();
      fillBytes(planes, yuvBytes);
      yRowStride = planes[0].getRowStride();
      final int uvRowStride = planes[1].getRowStride();
      final int uvPixelStride = planes[1].getPixelStride();

      imageConverter =
          new Runnable() {
            @Override
            public void run() {
              ImageUtils.convertYUV420ToARGB8888(
                  yuvBytes[0],
                  yuvBytes[1],
                  yuvBytes[2],
                  previewWidth,
                  previewHeight,
                  yRowStride,
                  uvRowStride,
                  uvPixelStride,
                  rgbBytes);
            }
          };

      postInferenceCallback =
          new Runnable() {
            @Override
            public void run() {
              image.close();
              isProcessingFrame = false;
            }
          };

      processImage();
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      Trace.endSection();
      return;
    }
    Trace.endSection();
  }

  @Override
  public synchronized void onStart() {
    LOGGER.d("onStart " + this);
    super.onStart();
  }

  @Override
  public synchronized void onResume() {
    LOGGER.d("onResume " + this);
    super.onResume();
    handlerThread = new HandlerThread("inference");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
  }

  @Override
  public synchronized void onPause() {
    LOGGER.d("onPause " + this);

    handlerThread.quitSafely();
    try {
      handlerThread.join();
      handlerThread = null;
      handler = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }

    super.onPause();
  }

  @Override
  public synchronized void onStop() {
    LOGGER.d("onStop " + this);
    super.onStop();
  }

  @Override
  public synchronized void onDestroy() {
    LOGGER.d("onDestroy " + this);
    mBTAdapter.disable();
    super.onDestroy();
  }

  protected synchronized void runInBackground(final Runnable r) {
    if (handler != null) {
      handler.post(r);
    }
  }

  @Override
  public void onRequestPermissionsResult(
      final int requestCode, final String[] permissions, final int[] grantResults) {
    if (requestCode == PERMISSIONS_REQUEST) {
      if (allPermissionsGranted(grantResults)) {
        setFragment();
      } else {
        requestPermission();
      }
    }
  }



  private static boolean allPermissionsGranted(final int[] grantResults) {
    for (int result : grantResults) {
      if (result != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  private boolean hasPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
    } else {
      return true;
    }
  }

  private void requestPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
        Toast.makeText(
                CameraActivity.this,
                "Camera permission is required for this demo",
                Toast.LENGTH_LONG)
            .show();
      }
      requestPermissions(new String[] {PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
    }
  }

  // Returns true if the device supports the required hardware level, or better.
  private boolean isHardwareLevelSupported(
      CameraCharacteristics characteristics, int requiredLevel) {
    int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
    if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
      return requiredLevel == deviceLevel;
    }
    // deviceLevel is not LEGACY, can use numerical sort
    return requiredLevel <= deviceLevel;
  }

  private String chooseCamera() {
    final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    try {
      for (final String cameraId : manager.getCameraIdList()) {
        final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

        // We don't use a front facing camera in this sample.
        final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
          continue;
        }

        final StreamConfigurationMap map =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map == null) {
          continue;
        }

        // Fallback to camera1 API for internal cameras that don't have full support.
        // This should help with legacy situations where using the camera2 API causes
        // distorted or otherwise broken previews.
        useCamera2API =
            (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
                || isHardwareLevelSupported(
                    characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
        LOGGER.i("Camera API lv2?: %s", useCamera2API);
        return cameraId;
      }
    } catch (CameraAccessException e) {
      LOGGER.e(e, "Not allowed to access camera");
    }

    return null;
  }

  protected void setFragment() {
    String cameraId = chooseCamera();

    Fragment fragment;
    if (useCamera2API) {
      CameraConnectionFragment camera2Fragment =
          CameraConnectionFragment.newInstance(
              new CameraConnectionFragment.ConnectionCallback() {
                @Override
                public void onPreviewSizeChosen(final Size size, final int rotation) {
                  previewHeight = size.getHeight();
                  previewWidth = size.getWidth();
                  CameraActivity.this.onPreviewSizeChosen(size, rotation);
                }
              },
              this,
              getLayoutId(),
              getDesiredPreviewFrameSize());

      camera2Fragment.setCamera(cameraId);
      fragment = camera2Fragment;
    } else {
      fragment =
          new LegacyCameraConnectionFragment(this, getLayoutId(), getDesiredPreviewFrameSize());
    }

    getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
  }

  protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    for (int i = 0; i < planes.length; ++i) {
      final ByteBuffer buffer = planes[i].getBuffer();
      if (yuvBytes[i] == null) {
        LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
        yuvBytes[i] = new byte[buffer.capacity()];
      }
      buffer.get(yuvBytes[i]);
    }
  }

  public boolean isDebug() {
    return debug;
  }

  protected void readyForNextImage() {
    if (postInferenceCallback != null) {
      postInferenceCallback.run();
    }
  }

  protected int getScreenOrientation() {
    switch (getWindowManager().getDefaultDisplay().getRotation()) {
      case Surface.ROTATION_270:
        return 270;
      case Surface.ROTATION_180:
        return 180;
      case Surface.ROTATION_90:
        return 90;
      default:
        return 0;
    }
  }


  @Override
  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    setUseNNAPI(isChecked);
    if (isChecked) apiSwitchCompat.setText("NNAPI");
    else apiSwitchCompat.setText("TFLITE");
  }

  @Override
  public void onClick(View v) {
    if (v.getId() == R.id.plus) {
      String threads = threadsTextView.getText().toString().trim();
      int numThreads = Integer.parseInt(threads);
      if (numThreads >= 9) return;
      numThreads++;
      threadsTextView.setText(String.valueOf(numThreads));
      setNumThreads(numThreads);
    } else if (v.getId() == R.id.minus) {
      String threads = threadsTextView.getText().toString().trim();
      int numThreads = Integer.parseInt(threads);
      if (numThreads == 1) {
        return;
      }
      numThreads--;
      threadsTextView.setText(String.valueOf(numThreads));
      setNumThreads(numThreads);
    }
  }


  protected void showResultsInBottomSheet(List<Recognition> results, float conf) {
    if (results != null && results.size() >= 3) {
      Recognition recognition = results.get(0);
      if (recognition != null && conf>=0.5) {
        if (recognition.getTitle() != null) {recognitionTextView.setText(recognition.getTitle());}
        String toSpeak = recognitionTextView.getText().toString();
        //Toast.makeText(getApplicationContext(), toSpeak,Toast.LENGTH_SHORT).show();
        if (!t1.isSpeaking() && mReadBuffer.getText() != "") t1.speak(toSpeak +"at"+mReadBuffer.getText(), TextToSpeech.QUEUE_FLUSH, null);
        else if (!t1.isSpeaking() && mReadBuffer.getText() == "") t1.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
      }
    }
  }


    protected void showFrameInfo(String frameInfo) {
    frameValueTextView.setText(frameInfo);
  }

  protected void showCropInfo(String cropInfo) {
    cropValueTextView.setText(cropInfo);
  }

  protected void showInference(String inferenceTime) {
    inferenceTimeTextView.setText(inferenceTime);
  }

  protected abstract void processImage();

  protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

  protected abstract int getLayoutId();

  protected abstract Size getDesiredPreviewFrameSize();

  protected abstract void setNumThreads(int numThreads);

  protected abstract void setUseNNAPI(boolean isChecked);
}
