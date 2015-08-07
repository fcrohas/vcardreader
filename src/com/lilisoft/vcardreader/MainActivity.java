package com.lilisoft.vcardreader;

import java.util.Locale;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.lilisoft.vcardreader.R;

public class MainActivity extends FragmentActivity { // implements CvCameraViewListener2 

	private static final String    TAG = "CaptureMobile::MainActivity";
			
	/**
	 * The {@link android.support.v4.view.PagerAdapter} that will provide
	 * fragments for each of the sections. We use a
	 * {@link android.support.v4.app.FragmentPagerAdapter} derivative, which
	 * will keep every loaded fragment in memory. If this becomes too memory
	 * intensive, it may be best to switch to a
	 * {@link android.support.v4.app.FragmentStatePagerAdapter}.
	 */
	SectionsPagerAdapter mSectionsPagerAdapter;

	/**
	 * The {@link ViewPager} that will host the section contents.
	 */
	ViewPager mViewPager;

	private CameraPreview camPreview = null;
	private ImageView MyCameraPreview = null;
	private PhotoProcess photoProcess = null;
	private ImageProcessing imageProcessing = null;
	private ReaderProcess readerProcess = null;
	public  ProgressDialog inProgress = null;

	@Override
	public View onCreateView(String name, Context context, AttributeSet attrs) {
		if (imageProcessing == null) {
			imageProcessing = new ImageProcessing();
		}
		
		if (readerProcess == null) {
			readerProcess = new ReaderProcess(context);
			readerProcess.loadLanguage("fra");
			readerProcess.setNextProcess(this);
		}
		
		// Camera view management
		View localView = super.onCreateView(name, context, attrs);
		SurfaceView camView = (SurfaceView)findViewById(R.id.surfaceCameraView);
		if ((camView!=null) && (camPreview==null)) {
			MyCameraPreview = (ImageView) findViewById(R.id.imageCameraView);
			camPreview = new CameraPreview( 320, 240, MyCameraPreview, camView);
			camPreview.setImageProcessing(imageProcessing);
			SurfaceHolder camHolder = camView.getHolder();
			camHolder.addCallback(camPreview);
			Button start = (Button)findViewById(R.id.buttonCamera);
			start.setOnClickListener(camPreview);
			camPreview.setNextProcess(this);
		}
		// Adjust view management
		RelativeLayout adjustView = (RelativeLayout)findViewById(R.id.adjustLayout);
		if ((adjustView!=null) && (photoProcess == null)) {
			photoProcess = (PhotoProcess) findViewById(R.id.surfaceAdjust);
			//photoProcess = new PhotoProcess(context);
			photoProcess.setImageProcessing(imageProcessing);
			//adjustView.addView(photoProcess);
			camPreview.setCaptureCallback(photoProcess);
			Button next = (Button)findViewById(R.id.buttonNext);
			Button adjust = (Button)findViewById(R.id.buttonAdjust);
			next.setOnClickListener(photoProcess);
			adjust.setOnClickListener(photoProcess);
			photoProcess.setNextProcess(this);
			photoProcess.setReader(readerProcess);
		}
		return localView;
	}
	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		// Create the adapter that will return a fragment for each of the three
		// primary sections of the app.
		mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

		// Set up the ViewPager with the sections adapter.
		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mSectionsPagerAdapter);
		
	}

	/**
	 * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
	 * one of the sections/tabs/pages.
	 */
	public class SectionsPagerAdapter extends FragmentPagerAdapter {

		public SectionsPagerAdapter(FragmentManager fm) {
			super(fm);
		}
		
		@Override
		public Fragment getItem(int position) {
			// getItem is called to instantiate the fragment for the given page.
			// Return a DummySectionFragment (defined as a static inner class
			// below) with the page number as its lone argument.
			Fragment fragment = null;
			Bundle args = new Bundle();			
			switch(position) {
				case 0 :
					fragment = new CameraSectionFragment();
					args.putInt(CameraSectionFragment.ARG_SECTION_NUMBER, position + 1);
					break;
				case 1 :
					fragment = new AdjustSectionFragment();
					args.putInt(AdjustSectionFragment.ARG_SECTION_NUMBER, position + 1);
					break;
				case 2 :
					fragment = new SendSectionFragment();
					args.putInt(SendSectionFragment.ARG_SECTION_NUMBER, position + 1);
					break;
			}
			fragment.setArguments(args);
			return fragment;
		}

		@Override
		public int getCount() {
			// Show 3 total pages.
			return 3;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			Locale l = Locale.getDefault();
			switch (position) {
			case 0:
				return getString(R.string.title_section1).toUpperCase(l);
			case 1:
				return getString(R.string.title_section2).toUpperCase(l);
			case 2:
				return getString(R.string.title_section3).toUpperCase(l);
			}
			return null;
		}
	}

	/**
	 * A dummy fragment representing a section of the app, but that simply
	 * displays dummy text.
	 */
	public static class CameraSectionFragment extends Fragment {
		/**
		 * The fragment argument representing the section number for this
		 * fragment.
		 */
		
		public static final String ARG_SECTION_NUMBER = "section_camera";

		public CameraSectionFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fcamerapreview, //R.layout.fcameraview,
					container, false);
			return rootView;
		}

	}

	public static class AdjustSectionFragment extends Fragment {
		/**
		 * The fragment argument representing the section number for this
		 * fragment.
		 */
		
		public static final String ARG_SECTION_NUMBER = "section_adjust";

		public AdjustSectionFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fadjustview,
					container, false);
			return rootView;
		}

	}
	
	public static class SendSectionFragment extends Fragment {
		/**
		 * The fragment argument representing the section number for this
		 * fragment.
		 */
		
		public static final String ARG_SECTION_NUMBER = "section_send";

		public SendSectionFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fsendview,
					container, false);
			return rootView;
		}

	}
	
	 @Override
	protected void onPause()
	{
	   if ( camPreview != null)
	     camPreview.onPause();
	   super.onPause();
	}	
	
	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
	    @Override
	    public void onManagerConnected(int status) {
	        switch (status) {
	            case LoaderCallbackInterface.SUCCESS:
	            {
	                Log.i(TAG, "OpenCV loaded successfully");
	            } break;
	            default:
	            {
	                super.onManagerConnected(status);
	            } break;
	        }
	    }
	};

	@Override
	public void onResume()
	{
	    super.onResume();
	    OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_8, this, mLoaderCallback);
	    if (camPreview != null) {
	    	camPreview.onResume();
	    }
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// TODO Auto-generated method stub
		switch(item.getItemId()) {
			case R.id.action_settings:
				//getFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsActivity()).commit();
				startActivityForResult(new Intent(this,SettingsActivity.class), 1);
				return true;
		}
		return super.onOptionsItemSelected(item);		
	}
}
