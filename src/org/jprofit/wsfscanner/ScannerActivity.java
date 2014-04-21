// The MIT License (MIT)
//
// Copyright (c) 2014  Jack Profit Jr.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in t he Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package org.jprofit.wsfscanner;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class ScannerActivity extends Activity {
	
	private final String TAG = "WSFScanner";
	private final String HTML_KEY = "html";
	private String webViewContent;
	private ProgressBar pbar;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Set layout
		setContentView(R.layout.activity_main);
		
		// Deal with orientation change restart by restoring webview content
		if (savedInstanceState != null) {
			webViewContent = savedInstanceState.getString(HTML_KEY);
			if (webViewContent != null) {
				updateWebView();
			}
		} 
		
		// Initialize members
		pbar = (ProgressBar) findViewById(R.id.progressBar);
		
		// Start in scanning mode automatically (the first time)
		if (savedInstanceState == null) {
			IntentIntegrator.initiateScan(this);
		}
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putString(HTML_KEY, webViewContent);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_scan) {
			webViewContent = null;
			updateWebView();
			IntentIntegrator.initiateScan(this);
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void onResume() {
		super.onResume();
	}
	
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		// retrieve scan result
		IntentResult scanningResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
		if (scanningResult != null) {
			//we have a scan result			
			String url = "https://secure4.gatewayticketing.com/WSDOT/account/TicketLookup.aspx?VisualID=" + scanningResult.getContents();
			Log.i(TAG, "Loading: " + url);
			new AsyncGetDocument().execute(url);
		} else {
			String err = "No scan data received";
		    Toast.makeText(getApplicationContext(), err, Toast.LENGTH_SHORT).show();
		    Log.e(TAG, err);
		}
	}

	private void updateWebView() {
		WebView myWebView = (WebView) findViewById(R.id.webview);
		myWebView.loadData((webViewContent == null) ? "" : webViewContent, "text/html", "UTF-8");
	}
	
	class AsyncGetDocument extends AsyncTask<String, Integer, String> {
		
		@Override
		protected String doInBackground(String... urlStr) {
			
			final int CHUNK_SIZE = 8*1024;
			
			// Initialize progress bar state
			pbar.setVisibility(1);
			pbar.setMax(100);
			pbar.setProgress(10);
			
			// THis is messy because WSF web page is not well formed. So we can't just use SAX/DOM parser
			// Instead we filter down to "interesting" part of page which is hopefully well formed
			HttpClient client = new DefaultHttpClient();
			HttpGet pageGet = new HttpGet(urlStr[0]);		
			String ticketHtml = null;
			try {
				HttpResponse response = client.execute(pageGet);
				Header[] clHeaders = response.getHeaders("Content-Length");
		        int totalSize = (clHeaders != null) ? Integer.parseInt(clHeaders[0].getValue()) : 0;
				InputStream is = response.getEntity().getContent();
				byte[] buf = new byte[CHUNK_SIZE];
				StringBuffer sb = new StringBuffer();
				int r;
				while((r = is.read(buf)) >= 0) {
					sb.append(new String(buf, 0, r, "UTF-8"));
					if (totalSize > 0) {
						publishProgress(100 * sb.length() / totalSize);
					}
				}

				// Snip everything between <div id=TicketLookup> and next </div>
				String rawHtml = sb.toString();
				Pattern pattern = Pattern.compile("<div id=\"TicketLookup\">.*?</div>", Pattern.DOTALL);
				Matcher matcher = pattern.matcher(rawHtml);

				if (matcher.find()) {
					// Looks like we need to remove comments as well
					ticketHtml = matcher.group();
					ticketHtml = ticketHtml.replaceAll("<!--.*", "");

					// Add first table column to data
					//ticketHtml = ticketHtml.replaceFirst("<td.*?></td>", "<td>  </td>");
					ticketHtml = ticketHtml.replaceFirst("<td width.*?></td>", "<td><b>Barcode: </b></td>");
					ticketHtml = ticketHtml.replaceFirst("<td></td>", "<td><b>PLU: </b></td>");
					ticketHtml = ticketHtml.replaceFirst("<td></td>", "<td><b>Item Name: </b></td>");
					ticketHtml = ticketHtml.replaceFirst("<td></td>", "<td><b>Description: </b></td>");
					ticketHtml = ticketHtml.replaceFirst("<td></td>", "<td><b>Price: </b></td>");
					ticketHtml = ticketHtml.replaceFirst("<td></td>", "<td><b>Expiration: </b></td>");
					ticketHtml = ticketHtml.replaceFirst("<td></td>", "<td><b>Uses Remaining: </b></td>");
					
					// Other random HTML fixups
					ticketHtml = ticketHtml.replaceFirst("History</td>", "<b>History</b></td>");
					
					// Now try an parse just Ticket lookup as XML
					//DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
					//DocumentBuilder db = factory.newDocumentBuilder();
					//doc = db.parse(new ByteArrayInputStream(ticketHtml.getBytes("UTF-8")));
				}
			} catch (Exception ex){
				Log.e(TAG, ex.toString());
			}
			return ticketHtml;
		}
		
		@Override
		protected void onProgressUpdate(Integer...vals) {
			pbar.setProgress(vals[0]);
		}
		
		@Override
		protected void onPostExecute(String doc) {
			if (doc != null) {
				
				// Progress complete
				pbar.setVisibility(0);
				pbar.setProgress(100);
				
				webViewContent = doc;
				updateWebView();
				
			} else {
				Toast.makeText(getApplicationContext(), "Failed to retrieve content from WSF", Toast.LENGTH_SHORT).show();
			}
		}
	}
}
