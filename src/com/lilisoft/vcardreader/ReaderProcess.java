package com.lilisoft.vcardreader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import org.xmlpull.v1.XmlSerializer;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Environment;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Xml;
import android.widget.TextView;

import com.googlecode.leptonica.android.Pixa;
import com.googlecode.tesseract.android.ResultIterator;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel;

public class ReaderProcess {
	
	private final TessBaseAPI baseApi;
	private static final String DATA_PATH = Environment.getExternalStorageDirectory().toString() + "/VCardReader/";
	private Bitmap photo = null;
	private ProgressDialog progress;
    private Context context;
    private ProcessAsync process;
    private Activity activity;
    private String fileName = "";
	
	
	public ReaderProcess(Context context) {
		this.context = context;
		// Init tesseract
		baseApi = new TessBaseAPI();
		baseApi.setDebug(true);
	}
	
	public void loadImage(Bitmap toocr) {
		Matrix matrix = new Matrix();
		matrix.postRotate(90);
		
		Bitmap temp = Bitmap.createBitmap(toocr, 0, 0, toocr.getWidth(), toocr.getHeight(), matrix, true);
		this.photo  = Bitmap.createScaledBitmap(temp, temp.getWidth(), temp.getHeight(), false);
		Date date = new Date();
		fileName = "vcard_"+Long.toString(date.getTime());
		
		File pictureFile = new File(DATA_PATH+fileName+".png");
	    try {
	        FileOutputStream fos = new FileOutputStream(pictureFile);
	        this.photo.compress(Bitmap.CompressFormat.PNG, 90, fos);
	        fos.close();
	    } catch (FileNotFoundException e) {
	        Log.d("VCardReader", "File not found: " + e.getMessage());
	    } catch (IOException e) {
	        Log.d("VCardReader", "Error accessing file: " + e.getMessage());
	    }  
	}
	
	public void loadLanguage(String lang) {
		baseApi.init(DATA_PATH, lang, TessBaseAPI.OEM_DEFAULT);	
		baseApi.setVariable("tessedit_pageseg_mode", "6");
	}

	public void buildXmlFile(String name) {
		File xmlFile = new File(DATA_PATH+name);
		FileWriter filewriter = null;
		try {
			filewriter = new FileWriter(xmlFile);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			Log.d("VCardReader","Can't create xml file");
			return;
		}
		XmlSerializer serializer = Xml.newSerializer();
		StringWriter writer = new StringWriter();
		try {
			baseApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO_ONLY);
			serializer.setOutput(writer);
			serializer.startDocument("UTF-8", true);
			serializer.startTag("", "blocks");
			Pixa block= baseApi.getRegions();
			Pixa line = baseApi.getTextlines();
			Pixa word = baseApi.getWords();
			ArrayList<Rect> boxes = block.getBoxRects();
			ArrayList<Rect> lines = line.getBoxRects();
			ArrayList<Rect> words = word.getBoxRects();
			for (int j=0; j< boxes.size(); j++)
			{
				Rect textblock = boxes.get(j);
				serializer.startTag("", "block");
				serializer.attribute("", "size", textblock.flattenToString());
				int linecount = 1;
				for (int i=0; i <lines.size(); i++) {
					Rect textLine = lines.get(i);
					if ((!textblock.contains(textLine)) && (!textblock.intersect(textLine)) )
							continue;
					// Write text line
					serializer.startTag("", "line");
					// write line id
					serializer.attribute("", "id", Integer.toString(linecount));
					serializer.attribute("", "size", textLine.flattenToString());
					for (int k=0; k<words.size();k++)
					{
						baseApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_WORD);						
						Rect textword = words.get(k);
						if ((!textLine.contains(textword)) && (!textLine.intersect(textword)))
								continue;
						// Set rectangle to read
						baseApi.setRectangle(textword);
						@SuppressWarnings("unused")
						String dummy = baseApi.getUTF8Text();
						ResultIterator it = baseApi.getResultIterator();
						if (it != null) {
							it.begin();
							String text;
							float conf = 0.0f;
							do {
								// Write text word
								serializer.startTag("", "word");
								serializer.attribute("", "size", textword.flattenToString());
								// get text
								if (it.getUTF8Text(PageIteratorLevel.RIL_WORD) != null) {
									text = it.getUTF8Text(PageIteratorLevel.RIL_WORD);
								} else {
									text = "";
								}
								// get confidence
								conf = it.confidence(PageIteratorLevel.RIL_WORD);
								// Write confidence
								serializer.attribute("", "confidence", Float.toString(conf));
								// write text
								serializer.text(text);
								// Close text word
								serializer.endTag("", "word");
							} while(it.next(PageIteratorLevel.RIL_WORD));
						}
					}
					// Close text line
					serializer.endTag("", "line");
					linecount++;
				}
				serializer.endTag("", "block");
			}
			serializer.endTag("", "blocks");
			serializer.endDocument();
			filewriter.append(writer.toString());
			filewriter.flush();
			filewriter.close();
		} catch (Exception e) {
			Log.d("VCardReader", "Error while building XML");
		}
	}
	
	public String processBitmap(Bitmap vcard) {
		baseApi.setDebug(true);
		baseApi.setImage(vcard);
		//baseApi.setPageSegMode(TessBaseAPI.OEM_DEFAULT); // good
		baseApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
		//progress.setTitle("Building Text");
		String text="";
		try {
			text = baseApi.getUTF8Text();
		} catch (Exception e) {
			Log.d("VCardReader","Reader exception :"+e.getMessage());
		}
		//progress.setTitle("Building XML");
		try {
			baseApi.setImage(vcard);
			buildXmlFile(fileName+".xml");
		} catch (Exception e) {
			Log.d("VCardReader","Reader xml exception :"+e.getMessage());
		}
		return text;
	}
	
	public void start() {
		progress = ProgressDialog.show(context, "Reading...", "Please Wait", true, false);
		process = new ProcessAsync(context,this);
		process.execute();
	}

	public void displayText(String result) {
		TextView text = (TextView)activity.findViewById(R.id.textResult);
		text.setMovementMethod(new ScrollingMovementMethod());
		text.setText(result);
	}
	
	public void setNextProcess(Activity activity) {
		this.activity = activity;
	}
	
	class ProcessAsync extends AsyncTask<Void, Void, String> {

		private ReaderProcess process;
		@Override
		protected void onPostExecute(String result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);
			this.process.displayText(result);
			this.process.progress.dismiss();
		}

		public ProcessAsync(Context context,ReaderProcess process) {
			this.process = process;
		}
		
		@Override
		protected String doInBackground(Void... params) {
			// TODO Auto-generated method stub
			return processBitmap(this.process.photo);
		}

	}	

}
