package com.lilisoft.vcardreader;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import android.graphics.PointF;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Path.FillType;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

public class PhotoProcess extends SurfaceView implements SurfaceHolder.Callback, PictureCallback, View.OnClickListener {

	private int viewSizeX = 0;
	private int viewSizeY = 0;
	private Bitmap displayPics = null;
	private Bitmap origPics = null;
	private SurfaceHolder holder = null;
	private Bitmap resultBitmap = null;
	private Matrix matrix;
	private Paint paint = null;
    static final int TOUCH_MODE_TAP = 1;  
    static final int TOUCH_MODE_DOWN = 2;  
    private ImageProcessing imageProcessing;
    private MainActivity activity;
    private AtomicBoolean processingDone;
    private ProgressDialog progress;
    private Context context;
    private ProcessAsync process;
    private ScaleGestureDetector mScaleDetector;
    private float mScaleFactor = 1.f;
    private float mLastTouchX;
    private float mLastTouchY;
    private float mPosX = 0;
    private float mPosY = 0;
    private static final int INVALID_POINTER_ID = -1;
    private int mActivePointerId = INVALID_POINTER_ID;
    private ReaderProcess reader = null;
    private List<PointF> points =null;
    private boolean contourFound = false;
    private boolean isAdjust = false;
    private boolean adjusting = false;
    private float selectScale = 1.0f;
    private GestureDetector gestureDetector = null;
	private boolean editing = false;
	private int editPointIndex = -1;

    
	public PhotoProcess(Context context, AttributeSet attrs) {
		super(context,attrs);
		this.context = context;
		holder = getHolder();
		holder.addCallback(this);
		matrix = new Matrix();
		matrix.postRotate(90);
		paint = new Paint(Paint.FILTER_BITMAP_FLAG);
		holder.setFormat(PixelFormat.TRANSPARENT);
	    processingDone = new AtomicBoolean();
	    processingDone.set(false);
	    // 	Create our ScaleGestureDetector
	    mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
	    gestureDetector = new GestureDetector(context, new GestureListener());
	}

	public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
	    // Raw height and width of image
	    final int height = options.outHeight;
	    final int width = options.outWidth;
	    int inSampleSize = 1;
	
	    if (height > reqHeight || width > reqWidth) {
	
	        final int halfHeight = height / 2;
	        final int halfWidth = width / 2;
	
	        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
	        // height and width larger than the requested height and width.
	        while ((halfHeight / inSampleSize) > reqHeight
	                && (halfWidth / inSampleSize) > reqWidth) {
	            inSampleSize *= 2;
	        }
	    }
	
	    return inSampleSize;
	}
	
	public static Bitmap decodeSampledBitmapFromResource(byte[] data, int reqWidth, int reqHeight) {
		return decodeSampledBitmapFromResource(data,reqWidth,reqHeight,0);
	}
	
	public static Bitmap decodeSampledBitmapFromResource(byte[] data, int reqWidth, int reqHeight, int offset) {

	    // First decode with inJustDecodeBounds=true to check dimensions
	    final BitmapFactory.Options options = new BitmapFactory.Options();
	    options.inJustDecodeBounds = true;
	    try {
	    	BitmapFactory.decodeByteArray( data, offset, data.length-offset, options );
	    }
	    catch (OutOfMemoryError e) {
	        Log.e( "PhotoProcess", "Out of memory decoding image from camera.", e );
	    }

	    // Calculate inSampleSize
	    options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

	    // Decode bitmap with inSampleSize set
	    options.inJustDecodeBounds = false;
	    //try {
	    	return BitmapFactory.decodeByteArray( data, offset, data.length-offset, options );
	    /*}
	    catch (OutOfMemoryError e) {
	        Log.e( "PhotoProcess", "Out of memory decoding image from camera.", e );
	        return null;
	    }*/

	}

	@Override
	public void onPictureTaken(byte[] data, Camera camera) {
		progress = ProgressDialog.show(this.activity, "Processing...", "Please Wait", true, false);
		displayPics = BitmapFactory.decodeByteArray(data, 0, data.length);
		origPics = BitmapFactory.decodeByteArray(data, 0, data.length);
		process = new ProcessAsync(context,this);
		process.execute();
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		setWillNotDraw(false);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
	    // Let the ScaleGestureDetector inspect all events.
	    mScaleDetector.onTouchEvent(ev);
	    gestureDetector.onTouchEvent(ev);
	    
	    final int action = ev.getAction();
	    switch (action & MotionEvent.ACTION_MASK) {
	    case MotionEvent.ACTION_DOWN: {
	        final float x = ev.getX();
	        final float y = ev.getY();
	        
	        mLastTouchX = x;
	        mLastTouchY = y;
	        mActivePointerId = ev.getPointerId(0);
	        // editing mode so move closest point
	        if (editing == true) {
	        	float finger = 10.0f * 10.0f;
	        	RectF boundingbox = new RectF(x-finger/2.0f,y-finger/2.0f,finger,finger);
	        	for (int i=0; i<4 ;i++) {
	        		if (boundingbox.contains(mPosY + (displayPics.getHeight() - points.get(i).y)*mScaleFactor,points.get(i).x*mScaleFactor)) {
	        			editPointIndex = i;
	        			break;
	        		}
	        	}
	        }
	        break;
	    }
	        
	    case MotionEvent.ACTION_MOVE: {
	        final int pointerIndex = ev.findPointerIndex(mActivePointerId);
	        final float x = ev.getX(pointerIndex);
	        final float y = ev.getY(pointerIndex);

	        // Only move if the ScaleGestureDetector isn't processing a gesture.
	        if (!mScaleDetector.isInProgress()) {
	            final float dx = x - mLastTouchX;
	            final float dy = y - mLastTouchY;
	            if ((editing == true ) && (editPointIndex != -1)) {
	            	points.get(editPointIndex).set( (y - mPosY) * 1.0f/mScaleFactor, (x - mPosX) * - 1.0f/mScaleFactor);
	            } else {
		            mPosX += dx;
		            mPosY += dy;
	            }

	            invalidate();
	        }

	        mLastTouchX = x;
	        mLastTouchY = y;

	        break;
	    }
	        
	    case MotionEvent.ACTION_UP: {
	        mActivePointerId = INVALID_POINTER_ID;
	        editPointIndex = -1;
	        break;
	    }
	        
	    case MotionEvent.ACTION_CANCEL: {
	        mActivePointerId = INVALID_POINTER_ID;
	        editPointIndex = -1;
	        break;
	    }
	    
	    case MotionEvent.ACTION_POINTER_UP: {
	        final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) 
	                >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
	        final int pointerId = ev.getPointerId(pointerIndex);
	        if (pointerId == mActivePointerId) {
	            // This was our active pointer going up. Choose a new
	            // active pointer and adjust accordingly.
	            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
	            mLastTouchX = ev.getX(newPointerIndex);
	            mLastTouchY = ev.getY(newPointerIndex);
	            mActivePointerId = ev.getPointerId(newPointerIndex);
	        }
	        editPointIndex = -1;
	        break;
	    }
	    }
	    
	    return true;
	}
	
	@SuppressLint("DrawAllocation")
	@Override
	protected void onDraw(Canvas canvas) {
		// TODO Auto-generated method stub
		super.onDraw(canvas);
		if (isAdjust == true) {
			Bitmap rotatePics = Bitmap.createBitmap(origPics ,0 ,0 ,origPics.getWidth(), origPics.getHeight(), matrix,true);
			Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
			canvas.drawBitmap(rotatePics,0,0,paint);
			canvas.rotate(90);
			resultBitmap = Bitmap.createBitmap(rotatePics); //.copy(Config.ARGB_8888, false);
			rotatePics.recycle();
			isAdjust = false;
			adjusting = true;
			invalidate();
		}
		if (processingDone.get() == true) {
			Bitmap rotatePics = Bitmap.createBitmap(displayPics ,0 ,0 ,displayPics.getWidth(), displayPics.getHeight(), matrix,true);
			Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
			canvas.drawBitmap(rotatePics,0,0,paint);
			canvas.rotate(90);
			resultBitmap = Bitmap.createBitmap(rotatePics); //.copy(Config.ARGB_8888, false);
			rotatePics.recycle();
			progress.dismiss();
			processingDone.set(false);
			invalidate();
		} else {
			if (resultBitmap == null)
				return;
			canvas.save();
			canvas.translate(mPosX, mPosY);
			canvas.scale(mScaleFactor, mScaleFactor);
			canvas.drawBitmap(resultBitmap,0,0,paint);			
			// draw contour point
			if ((points != null)  && (adjusting==true)) {
				if (points.size()>0) {
					Path path = new Path();
					Paint contour = new Paint();
					contour.setStyle(Paint.Style.FILL);
					contour.setAntiAlias(true);
					path.reset();
					path.moveTo(displayPics.getHeight() - points.get(0).y, points.get(0).x);
					path.lineTo(displayPics.getHeight() - points.get(1).y, points.get(1).x);
					path.lineTo(displayPics.getHeight() - points.get(2).y, points.get(2).x);
					path.lineTo(displayPics.getHeight() - points.get(3).y, points.get(3).x);
					path.close();
					// draw corner
					contour.setColor(Color.argb(178, 255, 0, 0));
					canvas.drawCircle(displayPics.getHeight() - points.get(0).y, points.get(0).x, 10.0f * selectScale, contour);
					canvas.drawCircle(displayPics.getHeight() - points.get(1).y, points.get(1).x, 10.0f * selectScale, contour);
					canvas.drawCircle(displayPics.getHeight() - points.get(2).y, points.get(2).x, 10.0f * selectScale, contour);
					canvas.drawCircle(displayPics.getHeight() - points.get(3).y, points.get(3).x, 10.0f * selectScale, contour);
					contour.setColor(Color.argb(75, 0, 0, 255));				
					canvas.drawPath(path, contour);
		        	float finger = 10.0f * 10.0f;
		        	//RectF boundingbox = new RectF(mLastTouchX-finger/2.0f,mLastTouchY-finger/2.0f,finger,finger);
		        	//contour.setColor(Color.argb(75, 0, 0, 255));
		        	//canvas.drawRect(boundingbox, contour);
				}
				
			}
			canvas.restore();
		}
		
	}

	public void setViewportSize(int width, int height) {
		this.viewSizeX = width;
		this.viewSizeY = height;
	}
	
    public void setImageProcessing(ImageProcessing process) {
    	this.imageProcessing = process;
    }

	public void setNextProcess(MainActivity activity) {
		this.activity = activity;
	}
	
	public void setContours(List<PointF> points, AtomicBoolean found) {
		this.points = points;
		this.contourFound = found.get();
	}
	
	class ProcessAsync extends AsyncTask<Void, Void, Bitmap> {

		private PhotoProcess process;
		private List<PointF> points = null;
		private AtomicBoolean found;
		@Override
		protected void onPostExecute(Bitmap result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);
			processingDone.set(true);
			process.setContours(points, found);
			process.invalidate();
		}

		public ProcessAsync(Context context,PhotoProcess process) {
			this.process = process;
			found = new AtomicBoolean();
			found.set(false);
		}
		
		@Override
		protected Bitmap doInBackground(Void... params) {
			// TODO Auto-generated method stub
			points = imageProcessing.detectSquare( displayPics, displayPics.getWidth(), displayPics.getHeight(), found);
			return displayPics;
		}

	}

	public void setReader(ReaderProcess reader) {
		this.reader = reader;
	}
	
	@Override
	public void onClick(View v) {
		switch(v.getId()) {
		case R.id.buttonNext:
			activity.mViewPager.setCurrentItem(2);
			reader.loadImage(displayPics);
			reader.start();
			isAdjust = false;
			break;
		case R.id.buttonAdjust:
			if (adjusting == true) {
				adjusting = false;
				processingDone.set(true);				
				invalidate();
			} else {
				adjusting = false;
				isAdjust = true;
			}
			invalidate();
			break;
		}
	}	

	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
	    @Override
	    public boolean onScale(ScaleGestureDetector detector) {
	        mScaleFactor *= detector.getScaleFactor();
	        // Don't let the object get too small or too large.
	        mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 5.0f));
	        invalidate();
	        return true;
	    }
	}

	private class GestureListener extends GestureDetector.SimpleOnGestureListener {
	    // event when double tap occurs
	    @Override
	    public boolean onDoubleTap(MotionEvent e) {
	    	if (adjusting == true) {
	    		if (editing == true) {
	    			selectScale = 1.0f;
	    			editing = false;
	    		} else {
	    			selectScale = 3.0f;
	    			editing = true;
	    		}
	    		invalidate();
	    	}
	        return true;
	    }
	}
}
