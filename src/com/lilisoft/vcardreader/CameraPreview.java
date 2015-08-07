package com.lilisoft.vcardreader;

import java.io.IOException;
import java.util.List;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

public class CameraPreview implements SurfaceHolder.Callback, Camera.PreviewCallback,View.OnClickListener, AutoFocusCallback
{
  private Camera mCamera = null;
  private ImageView MyCameraPreview = null;
  private SurfaceView MyCameraView = null;
  private Bitmap bitmap = null;
  private byte[] FrameData = null;
  private int imageFormat;
  private int PreviewSizeWidth;
  private int PreviewSizeHeight;
  private boolean bProcessing = false;
  private ImageProcessing imageProcessing;
  private int viewSizeX = 540;
  private int viewSizeY = 733;
  private boolean detectFirst = false;
  private PhotoProcess captureCallback;
  private MainActivity activity; 
  
  Handler mHandler = new Handler(Looper.getMainLooper());
  
  public CameraPreview(int PreviewlayoutWidth, int PreviewlayoutHeight,
     ImageView CameraPreview, SurfaceView CameraView)
  {
    PreviewSizeWidth = PreviewlayoutWidth;
    PreviewSizeHeight = PreviewlayoutHeight;
    MyCameraPreview = CameraPreview;
    MyCameraView = CameraView;
  }
 
  @Override
  public void onPreviewFrame(byte[] arg0, Camera arg1) 
  {
    // At preview mode, the frame data will push to here.
    if (imageFormat == ImageFormat.NV21)
    {
      //We only accept the NV21(YUV420) format.
      if ( !bProcessing )
      {
        FrameData = arg0;
        mHandler.post(DoImageProcessing);
        //this.submitFocusAreaRect(focusRect);
      }
    }
  }
  
  public void onPause()
  {
	if (mCamera != null) {
		mCamera.stopPreview();
	}
  }

  public void onResume()
  {
	if (mCamera != null) {
		mCamera.startPreview();
	}
  }
  
  @Override
  public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) 
  {
    Parameters parameters;
   
    parameters = mCamera.getParameters();
    // Set the camera preview size
    List<Size> previewSize = parameters.getSupportedPreviewSizes();
    int listmiddle = previewSize.size() / 2;
    PreviewSizeHeight = previewSize.get(listmiddle).height;
    PreviewSizeWidth = previewSize.get(listmiddle).width;
    parameters.setPreviewSize(PreviewSizeWidth, PreviewSizeHeight);
    List<Size> picturesSize = parameters.getSupportedPictureSizes();
    parameters.setPictureSize(picturesSize.get(picturesSize.size()/2).width, picturesSize.get(picturesSize.size()/2).height);
    imageFormat = parameters.getPreviewFormat();
    //arg0.setFormat(PixelFormat.TRANSPARENT);
    //parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
    //parameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
    //parameters.setSceneMode(Parameters.SCENE_MODE_BARCODE);
    mCamera.setParameters(parameters);
    mCamera.setDisplayOrientation(90);
    //mCamera.startPreview();
  }
 
  @Override
  public void surfaceCreated(SurfaceHolder arg0) 
  {
	    mCamera = Camera.open();
	    try
	    {
	      // If did not set the SurfaceHolder, the preview area will be black.
	      mCamera.setPreviewDisplay(arg0);
	      mCamera.setPreviewCallback(this);
	    } 
	    catch (IOException e)
	    {
	      mCamera.release();
	      mCamera = null;
	    }
  }

  public void setViewportSize(int x, int y) {
	  this.viewSizeY = y;
	  this.viewSizeX = x;
  }
  
  public void setParameters(Parameters parameters, boolean type) {
	  if (type) {
		  	List<String> flashMode = parameters.getSupportedFlashModes();
		  	if (flashMode.contains(Parameters.FLASH_MODE_AUTO)) {
		  		parameters.setFlashMode(Parameters.FLASH_MODE_AUTO);	
		  	}
		  	List<String> focusModes = parameters.getSupportedFocusModes();
		  	if (focusModes.contains(Parameters.FOCUS_MODE_AUTO)) {
			  	parameters.setFocusMode(Parameters.FOCUS_MODE_AUTO);		  		
		  	}
	  } else {
		    /*
		  	List<String> flashMode = parameters.getSupportedFlashModes();
		  	if (flashMode.contains(Parameters.FLASH_MODE_TORCH)) {
		  		parameters.setFlashMode(Parameters.FLASH_MODE_TORCH);	
		  	}*/
		  	List<String> focusModes = parameters.getSupportedFocusModes();
		  	if (focusModes.contains(Parameters.FOCUS_MODE_EDOF)) {
			  	parameters.setFocusMode(Parameters.FOCUS_MODE_EDOF);		  		
		  	}
	  }
  }
  
  public void startCamera() {
	    imageProcessing.onCameraViewStarted(PreviewSizeWidth, PreviewSizeHeight);
	    bitmap = Bitmap.createBitmap(viewSizeX, viewSizeY, Bitmap.Config.ARGB_8888);
	    bitmap.eraseColor(Color.TRANSPARENT);
	    if (mCamera != null) {
	    	Parameters parameters;
		  	parameters = mCamera.getParameters();	    	
	    	setParameters(parameters, false);
	    	mCamera.setParameters(parameters);
	    	mCamera.startPreview();
	    }
  }
  
  @Override
  public void surfaceDestroyed(SurfaceHolder arg0) 
  {
	 imageProcessing.onCameraViewStopped();
     mCamera.setPreviewCallback(null);
	 mCamera.stopPreview();
	 mCamera.release();
	 mCamera = null;
  }
 
  public void takePicture() {
	Parameters parameters;
	parameters = mCamera.getParameters();
	setParameters(parameters, true);
	mCamera.setParameters(parameters);
	mCamera.takePicture(null, null, null, captureCallback);
  }
  
  private Runnable DoImageProcessing = new Runnable() 
  {
	private Bitmap draw = null;
	private Canvas canvas = null;
	private Path path = null;
	private Paint paint = null;
	private float scaley = 1.0f;
	private float scalex = 1.0f;
    public void run() 
    {
      //Log.i("MyRealTimeImageProcessing", "DoImageProcessing():");
      bProcessing = true;
      List<PointF> results = imageProcessing.processFrame(FrameData,bitmap); //pixels);
      if (results == null) {
    	  bProcessing = false;
    	  return;
      }
      if (draw == null) {
    	  draw = Bitmap.createBitmap(MyCameraPreview.getWidth(), MyCameraPreview.getHeight(), Config.ARGB_8888);
    	  scalex = (float)MyCameraPreview.getWidth() / (float)PreviewSizeHeight;
    	  scaley = (float)MyCameraPreview.getHeight() / (float)PreviewSizeWidth;
    	  canvas = new Canvas(draw);
          path = new Path();
          paint = new Paint();
          paint.setColor(Color.argb(75, 0, 0, 255));
          paint.setStrokeWidth(2.0f);
          paint.setStyle(Paint.Style.FILL);
          paint.setAntiAlias(true);
      }
      draw.eraseColor(Color.TRANSPARENT);
      if (results.size() == 0) {
    	  MyCameraPreview.setImageBitmap(draw);
    	  bProcessing = false;
    	  return;
      }
	  path.reset();
	  path.moveTo((PreviewSizeHeight - results.get(0).y) * scaley, results.get(0).x * scalex);
	  path.lineTo((PreviewSizeHeight - results.get(1).y) * scaley, results.get(1).x * scalex);
	  path.lineTo((PreviewSizeHeight - results.get(2).y) * scaley, results.get(2).x * scalex);
	  path.lineTo((PreviewSizeHeight - results.get(3).y) * scaley, results.get(3).x * scalex);
	  path.close();
      canvas.drawPath(path, paint);
      MyCameraPreview.setImageBitmap(draw);
      bProcessing = false;
    }
  };

  @Override
  public void onClick(View v) {
	  	if (!detectFirst) {
	  		// Start square detection
			Button buttonPics = (Button)v.findViewById(R.id.buttonCamera);
			buttonPics.setText(R.string.button_pics);
			viewSizeX = MyCameraView.getWidth();
			viewSizeY = MyCameraView.getHeight();
			imageProcessing.setViewportSize(viewSizeX, viewSizeY);
			captureCallback.setViewportSize(viewSizeX, viewSizeY);
			startCamera();
			detectFirst = true;
	  	} else {
	  		// Take picture
	        mCamera.autoFocus(this);
	  		detectFirst = false;
			Button buttonPics = (Button)v.findViewById(R.id.buttonCamera);
			buttonPics.setText(R.string.button_start);
	  	}
  }
  
  public void setCaptureCallback(PhotoProcess callback) {
	  this.captureCallback = callback;
  }
  
  public void setImageProcessing(ImageProcessing process) {
	  this.imageProcessing = process;
  }

	@Override
	public void onAutoFocus(boolean success, Camera camera) {
		if (success) {
			takePicture();
			activity.mViewPager.setCurrentItem(1);			
		}
		
	}  
	
	public void setNextProcess(MainActivity activity) {
		this.activity = activity;
	}
}
