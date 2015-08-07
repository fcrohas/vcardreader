package com.lilisoft.vcardreader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.utils.Converters;
import android.graphics.PointF;

import android.graphics.Bitmap;
import android.util.Log;

public class ImageProcessing {

	private Mat blurred;
	private Size ksize;
	private Size outputSize;
	private Mat edges;
	private Mat gray;
	private List<MatOfPoint> contours;
	private List<MatOfPoint> squares;
	private MatOfPoint2f approxContour;
	private MatOfPoint2f contour2f;
	private MatOfPoint contour;
	private Mat inputFrame;
	private Mat resultFrame;
	private Mat outputFrame;
	private Mat pyr;
	private boolean initialized = false;
	private List<Point> srcpoints = null;
	private List<PointF> androidpoints = null;
	
	public ImageProcessing() {
		
	}

	public void onCameraViewStarted(int width, int height) {
		blurred = new Mat();
		edges = new Mat();
		gray = new Mat();
		pyr = new Mat();
		ksize = new Size(9,9);
		contour2f = new MatOfPoint2f();
		contour = new MatOfPoint();
		approxContour = new MatOfPoint2f();
		contours = new ArrayList<MatOfPoint>();
		squares  = new ArrayList<MatOfPoint>();
		srcpoints = new ArrayList<Point>();
		androidpoints = new ArrayList<PointF>();
		inputFrame = new Mat(height+height/2, width, CvType.CV_8UC1);
		resultFrame = new Mat(height,width, CvType.CV_8UC4);	
		outputFrame = new Mat(height,width, CvType.CV_8UC4);
	}
	
	public void setViewportSize(int width, int height) {
		outputSize = new Size( width,height);
		initialized = true;		
	}
	
	public void onCameraViewStopped() {
	}
	
	public double angle( Point pt1, Point pt2, Point pt0 ) {
	    double dx1 = pt1.x - pt0.x;
	    double dy1 = pt1.y - pt0.y;
	    double dx2 = pt2.x - pt0.x;
	    double dy2 = pt2.y - pt0.y;
	    return (dx1*dx2 + dy1*dy2)/Math.sqrt((dx1*dx1 + dy1*dy1)*(dx2*dx2 + dy2*dy2) + 1e-10);
	}	

	public List<Point> sortCorners(List<Point> corners, Point center)
	{
	    List<Point> top = new ArrayList<Point>();
	    List<Point> bot = new ArrayList<Point>();

	    for (int i = 0; i < corners.size(); i++)
	    {
	        if (corners.get(i).y < center.y)
	            top.add(corners.get(i));
	        else
	            bot.add(corners.get(i));
	    }

	    Point tl = top.get(0).x > top.get(1).x ? top.get(1) : top.get(0);
	    Point tr = top.get(0).x > top.get(1).x ? top.get(0) : top.get(1);
	    Point bl = bot.get(0).x > bot.get(1).x ? bot.get(1) : bot.get(0);
	    Point br = bot.get(0).x > bot.get(1).x ? bot.get(0) : bot.get(1);
	    List<Point> output = new ArrayList<Point>();
	    //corners.clear();
	    output.add(tl);
	    output.add(tr);
	    output.add(br);
	    output.add(bl);
	    return output;
	}
	
	public List<MatOfPoint> find_largest_square(List<MatOfPoint> squares) {
	    if (squares.size() == 0) {
	        return null;
	    }

	    int max_width = 0;
	    int max_height = 0;
	    int max_square_idx = 0;

	    for (int i = 0; i < squares.size(); i++) {
	        Rect rectangle = Imgproc.boundingRect(squares.get(i));
	        if ((rectangle.width >= max_width) && (rectangle.height >= max_height)) {
	            max_width = rectangle.width;
	            max_height = rectangle.height;
	            max_square_idx = i;
	        }
	    }
	    List<MatOfPoint> result = new ArrayList<MatOfPoint>();
	    result.add(squares.get(max_square_idx));
	    return result;

	}
	
	//public Mat processFrame(CvCameraViewFrame inputFrame) {
	public List<PointF> processFrame(byte[] FrameData, Bitmap pixels) {
		if (initialized == false) {
			return androidpoints;
		}
		contours.clear();
		squares.clear();
		inputFrame.put(0, 0, FrameData);
//		Mat frame = resultFrame.clone();
		Imgproc.cvtColor(inputFrame, gray, Imgproc.COLOR_BayerGR2GRAY);
		// Blurring
		Imgproc.GaussianBlur(gray, blurred, ksize, 0);
		//Imgproc.medianBlur(gray, blurred, 37);
		// Edge detection
		Imgproc.Canny(blurred, edges, 15, 20, 3 , false);
		Imgproc.dilate(edges, edges, new Mat());
		// Contour detection
		Imgproc.findContours(edges, contours, new Mat(), Imgproc.RETR_LIST , Imgproc.CHAIN_APPROX_SIMPLE);
		// Find only needed contours
		for (int i=0;i < contours.size(); i++) {
			contours.get(i).convertTo(contour2f, CvType.CV_32FC2);
			Imgproc.approxPolyDP(contour2f, approxContour, Imgproc.arcLength(contour2f, true)*0.015, true);
			if (approxContour.total() == 4 && 
                    Math.abs(Imgproc.contourArea(approxContour)) > 1000 &&
                    Imgproc.isContourConvex(new MatOfPoint(approxContour.toArray())))
            {
				double maxCosine = 0;
				double minCosine = 0;
				for (int j = 2; j < approxContour.total()+1; j++)
				{
						// find the maximum cosine of the angle between joint edges
						Point[] approx = approxContour.toArray();
						double cosine = angle(approx[j%approx.length], approx[j-2], approx[j-1]);
						maxCosine = Math.max(maxCosine, cosine);
						minCosine = Math.max(minCosine, cosine);
				}
				approxContour.convertTo(contour, CvType.CV_32S);
				if (minCosine >= -0.1 && maxCosine < 0.3)
                    squares.add(contour);				
            }
		}
		
		// Find largest square
		List<MatOfPoint> largest = find_largest_square(squares);

		if (largest != null) {
			largest.get(0).convertTo(contour2f, CvType.CV_32FC2);
			Point[] contour = contour2f.toArray();
			androidpoints.clear();
			androidpoints.add(new PointF((float)contour[0].x,(float)contour[0].y));
			androidpoints.add(new PointF((float)contour[1].x,(float)contour[1].y));
			androidpoints.add(new PointF((float)contour[2].x,(float)contour[2].y));
			androidpoints.add(new PointF((float)contour[3].x,(float)contour[3].y));
//			for (int i=0; i < largest.size();i++)
//				Imgproc.drawContours(frame, largest, i, new Scalar(0,255,0),2);
		}
//		outputFrame = frame.t();
//		Core.flip(frame.t(), outputFrame, 1);
//		Imgproc.resize(outputFrame, outputFrame, outputSize);
//		
//		Utils.matToBitmap(outputFrame, pixels);
		return androidpoints;
	}

	public List<PointF> detectSquare( Bitmap pixels, int width, int height, AtomicBoolean found) {
		found.set(false);
		if (initialized == false) {
			return androidpoints;
		}
		contours.clear();
		squares.clear();
		double scale = 2.0;
		Mat photoFrame = new Mat();
		Utils.bitmapToMat(pixels,photoFrame); //   new Mat(height+height/2, width, CvType.CV_8UC1);
		//photoFrame.put(0, 0, FrameData);
		Mat frame = photoFrame.clone();
		Size imgSize = photoFrame.size(); 
		//Size dstSize = new Size(imgSize.width/3, imgSize.height/3);
		imgSize.width = imgSize.width / scale;
		imgSize.height = imgSize.height / scale;
		Imgproc.pyrDown(photoFrame, pyr, imgSize);
		//Imgproc.pyrUp(pyr, pyr, photoFrame.size());
		List<Mat> gray0=new ArrayList<Mat>();
        List<Mat> timing1=new ArrayList<Mat>();
		// Blurring
		//Imgproc.GaussianBlur(pyr, blurred, new Size(19,19), 0);
		Log.d("VCardReader","pyr channels count is "+pyr.channels());        
		Imgproc.medianBlur(pyr, blurred, 17);
		// Color
		Log.d("VCardReader","Blurred channels count is "+blurred.channels());
		if (blurred.channels() > 2) {
			Imgproc.cvtColor(blurred, gray, Imgproc.COLOR_RGBA2GRAY,1);
		} else {
			blurred.copyTo(gray);
		}
			
		Imgproc.equalizeHist(gray, gray);
		// Resize picture to speed up processing
		//Imgproc.resize(gray, gray, new Size(gray.cols()/3,gray.rows()/3));
		//Imgproc.resize(blurred, blurred, new Size(blurred.cols()/3,blurred.rows()/3));
        //timing1.add(pyr);
        timing1.add(blurred);
        timing1.add(gray);
        //timing1.add(gray);
        gray0.add(new Mat(blurred.size(),CvType.CV_8UC1 ));
        gray0.add(new Mat(blurred.size(),CvType.CV_8UC1 ));        
        
		for (int c=0; c<blurred.channels(); c++) {
			
			int ch[] = { c,0 };
			MatOfInt fromto = new MatOfInt(ch);
			Core.mixChannels(timing1, gray0, fromto);
			
			for (int l=0; l < gray0.size(); l++ ) { //
				Mat output=gray0.get(l);
				if (l == 0) {
					// Edge detection
					Imgproc.Canny(output, edges, 15, 20, 3 , false);
					Imgproc.dilate(edges, edges, new Mat());
					
				} else {
					//Imgproc.adaptiveThreshold(output, edges, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU, 11, 2.0);
					Imgproc.threshold(output, edges, 127, 255, Imgproc.THRESH_BINARY);
					//Imgproc.dilate(edges, edges, new Mat());
				}

				// Contour detection
				Imgproc.findContours(edges, contours, new Mat(), Imgproc.RETR_LIST , Imgproc.CHAIN_APPROX_SIMPLE);
				// Find only needed contours
				for (int i=0;i < contours.size(); i++) {
					contours.get(i).convertTo(contour2f, CvType.CV_32FC2);
					Imgproc.approxPolyDP(contour2f, approxContour, Imgproc.arcLength(contour2f, true)*0.015, true);
					if (approxContour.total() == 4 && 
		                    Math.abs(Imgproc.contourArea(approxContour)) > 1000 &&
		                    Imgproc.isContourConvex(new MatOfPoint(approxContour.toArray())))
		            {
						double maxCosine = 0;
						double minCosine = 0;
						for (int j = 2; j < approxContour.total()+1; j++)
						{
								// find the maximum cosine of the angle between joint edges
								Point[] approx = approxContour.toArray();
								double cosine = angle(approx[j%approx.length], approx[j-2], approx[j-1]);
								maxCosine = Math.max(maxCosine, cosine);
								minCosine = Math.max(minCosine, cosine);
						}
						approxContour.convertTo(contour, CvType.CV_32S);
						if (minCosine >= -0.1 && maxCosine < 0.3)
		                    squares.add(contour);				
						
		            }
				}
			}				
		}
		// Find largest square
		List<MatOfPoint> largest = find_largest_square(squares);
		if (largest != null) {
//			if (largest != null) {
//				for (int i=0; i < largest.size();i++)
//					Imgproc.drawContours(photoFrame, largest, i, new Scalar(0,255,0),5);
//			}
			found.set(true);
			androidpoints.clear();
			// Sort corner
			Moments moments = Imgproc.moments(largest.get(0));
			Point center = new Point(0,0);
			center.x = moments.get_m10() / moments.get_m00();
			center.y = moments.get_m01() / moments.get_m00();
			
			List<Point> points = largest.get(0).toList();
			points = sortCorners(points, center);
			srcpoints.clear();
			srcpoints.add(new Point(points.get(0).x*scale,points.get(0).y*scale));
			srcpoints.add(new Point(points.get(1).x*scale,points.get(1).y*scale));
			srcpoints.add(new Point(points.get(2).x*scale,points.get(2).y*scale));
			srcpoints.add(new Point(points.get(3).x*scale,points.get(3).y*scale));
//			srcpoints.add(points.get(0));			
//			srcpoints.add(points.get(1));
//			srcpoints.add(points.get(2));
//			srcpoints.add(points.get(3));
			List<Point> dstPoints = new ArrayList<Point>();
			dstPoints.add(new Point(0,0));
			dstPoints.add(new Point(frame.width(),0));
			dstPoints.add(new Point(frame.width(),frame.height()));
			dstPoints.add(new Point(0,frame.height()));
//			if ((points.get(1).x-points.get(0).x)<(points.get(2).y-points.get(1).y)) {
//				dstPoints.add(new Point(frame.width(),0));
//				dstPoints.add(new Point(frame.width(),frame.height()));
//				dstPoints.add(new Point(0,frame.height()));
//			} else {
//				dstPoints.add(new Point(0,frame.height()));
//				dstPoints.add(new Point(frame.width(),frame.height()));
//				dstPoints.add(new Point(frame.width(),0));
//			}
			Mat dst = new Mat();
			Mat src = new Mat();
			dst = Converters.vector_Point_to_Mat(dstPoints);
			src = Converters.vector_Point_to_Mat(srcpoints);
			dst.convertTo(dst, CvType.CV_32FC2 );
			src.convertTo(src, CvType.CV_32FC2 );
			// Transform largest
			Mat transmtx = Imgproc.getPerspectiveTransform(src, dst);
			Imgproc.warpPerspective(photoFrame, frame, transmtx, frame.size());
			androidpoints.add(new PointF((float)(points.get(0).x*scale),(float)(points.get(0).y*scale)));
			androidpoints.add(new PointF((float)(points.get(1).x*scale),(float)(points.get(1).y*scale)));
			androidpoints.add(new PointF((float)(points.get(2).x*scale),(float)(points.get(2).y*scale)));
			androidpoints.add(new PointF((float)(points.get(3).x*scale),(float)(points.get(3).y*scale)));
		}
		//outputFrame = frame.t();
		//Core.flip(frame.t(), outputFrame, 1);
		//pixels = Bitmap.createBitmap(frame.cols(), frame.rows(), Config.ARGB_8888);
		Imgproc.resize(frame, frame, new Size(width,height));
		//Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGBA2GRAY);
		//Imgproc.adaptiveThreshold(frame, frame, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 2.0);
		Utils.matToBitmap(frame, pixels);
		return androidpoints;
	}	
}
