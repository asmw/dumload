package com.joshuawise.dumload;

import java.io.InputStream;
import java.io.OutputStream;

import com.jcraft.jsch.*;
import java.lang.Boolean;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.app.PendingIntent;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.app.NotificationManager;
import android.app.Notification;
import android.os.Handler;
import android.os.Messenger;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.widget.RemoteViews;

public class Uploader extends Service implements Runnable, UserInfo, UIKeyboardInteractive {
	private Uri uri;
	private String homedir;
	private Thread me;
	private static final int HELPME_ID = 1;
	private RemoteViews remote;
	private int thenotifid;
	private Notification thenotif;
	private String headline;
	
	private InputStream is;
	
	public Object _theObject;

	private void sayNullNotification(final String scroller, final String headline, final String description)
	{
		int bogon = (int)SystemClock.elapsedRealtime();
		NotificationManager mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		Notification notification = new Notification(R.drawable.icon, scroller, System.currentTimeMillis());

		Intent intent = new Intent(this, NotifSlave.class);
					
		intent.setAction("com.joshuawise.dumload.NotifSlave");
		/* no extras to make the notifslave die */
		intent.setData((Uri.parse("suckit://"+SystemClock.elapsedRealtime())));
				
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, 0);
		notification.defaults |= Notification.DEFAULT_VIBRATE;
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		notification.setLatestEventInfo(getApplicationContext(), headline, description, contentIntent);
				
		mNotificationManager.notify(bogon, notification);
	}

	private Object /* pick one type, and fixate on it */ dance(final String type, final String text)	/* for inside the thread */
	{
		final Uploader thisupl = this;
		final Message msg = Message.obtain();
		
		/* t(*A*t) */
		Thread t = new Thread() {
			public void run() {
				Looper.prepare();
				int bogon = (int)SystemClock.elapsedRealtime();
				
				NotificationManager mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
				Notification notification = new Notification(R.drawable.icon, "Dumload prompt", System.currentTimeMillis());
				
				Handler h = new Handler() {
					public void handleMessage(Message M) {
						msg.copyFrom(M);
						Looper.myLooper().quit();
					}
				};
				Messenger m = new Messenger(h);
				
				Intent intent = new Intent(thisupl, NotifSlave.class);
					
				intent.setAction("com.joshuawise.dumload.NotifSlave");
				intent.putExtra("com.joshuawise.dumload.returnmessenger", m);
				intent.putExtra("com.joshuawise.dumload.reqtype", type);
				intent.putExtra("com.joshuawise.dumload.prompt", text);
				intent.setData((Uri.parse("suckit://"+SystemClock.elapsedRealtime())));
				
				PendingIntent contentIntent = PendingIntent.getActivity(thisupl, 0, intent, 0);
				notification.defaults |= Notification.DEFAULT_VIBRATE;
				notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
				notification.setLatestEventInfo(getApplicationContext(), "I've been had!", "Dumload needs your input.", contentIntent);
				
				Log.e("Dumload.Uploader[thread]", "Notifying...");
				
				mNotificationManager.notify(bogon, notification);
				
				Log.e("Dumload.Uploader[thread]", "About to go to 'sleep'...");
				Looper.loop();
				Log.e("Dumload.Uploader[thread]", "And we're alive!");
					
				Log.e("Dumload.Uploader[thread]", "result was: "+(Integer.toString(msg.arg1)));
				
				mNotificationManager.cancel(bogon);
			}
		};
		
		t.start();
		try {
			t.join();
		} catch (Exception e) {
			return null;
		}
		
		if (type.equals("yesno"))
			return new Boolean(msg.arg1 == 1);
		else if (type.equals("message"))
			return null;
		else if (type.equals("password")) {
			if (msg.arg1 == 0)
				return null;
			Bundle b = msg.getData();
			return b.getString("response");
		} else
			return null;
	}
	
	/* UserInfo bits */
	String _password = null;
	public String getPassword()
	{
		return _password;
	}
	public boolean promptPassword(String message)
	{
		_password = (String)dance("password", message); 
		return (_password != null);
	}
	
	String _passphrase = null;
	public String getPassphrase()
	{
		return _passphrase;
	}
	public boolean promptPassphrase(String message)
	{
		_passphrase = (String)dance("password", message); 
		return (_passphrase != null);
	}
	
	public boolean promptYesNo(String str)
	{
		return ((Boolean)dance("yesno", str)).booleanValue();
	}
	
	public void showMessage(String str)
	{
		dance("message", str);
	}
	
	public String[] promptKeyboardInteractive(String dest, String name, String instr, String[] prompt, boolean[] echo)
	{
		int i;
		String [] responses = new String[prompt.length];
		
		Log.e("Dumload.Uploader", "dest: "+dest);
		Log.e("Dumload.Uploader", "name: "+name);
		Log.e("Dumload.Uploader", "instr: "+instr);
		for (i = 0; i < prompt.length; i++)
		{
			responses[i] = (String) dance("password", "[" + dest + "]\n" + prompt[i]);
			if (responses[i] == null)
				return null;
		}
		return responses;
	}
	
	private void expect_ack(InputStream in) throws Exception, java.io.IOException
	{
		int b = in.read();
		
		if (b == -1)
		{
			throw new Exception("unexpected EOF from remote end");
		}
		
		if (b == 1 /* error */ || b == 2 /* fatal error */)
		{
			StringBuffer sb = new StringBuffer();
			int c = 0;
			
			while ((c = in.read()) != '\n')
				sb.append((char)c);
			
			throw new Exception("error from remote end: " + sb.toString());
		}
	}
	
	private void set_up_notif(final String _headline)
	{
		NotificationManager mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		thenotif = new Notification(R.drawable.icon, headline, System.currentTimeMillis());
		thenotifid = (int)SystemClock.elapsedRealtime();

		Intent intent = new Intent(this, NotifSlave.class);
		
		headline = _headline;
					
		intent.setAction("com.joshuawise.dumload.NotifSlave");
		/* no extras to make the notifslave die */
		intent.setData((Uri.parse("suckit://"+SystemClock.elapsedRealtime())));
				
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, 0);
		thenotif.defaults |= 0;
		thenotif.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
		
		remote = new RemoteViews(getPackageName(), R.layout.textnotif);
		remote.setImageViewResource(R.id.image, R.drawable.icon);
		remote.setTextViewText(R.id.headline, headline);
		remote.setTextViewText(R.id.status, "Beginning upload...");
		thenotif.contentView = remote;
		thenotif.contentIntent = contentIntent;
				
		mNotificationManager.notify(thenotifid, thenotif);
	}
	
	private void destroy_notif()
	{
		NotificationManager mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancel(thenotifid);
	}
	
	private void update_notif(String text)
	{
		NotificationManager mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		
		remote = new RemoteViews(getPackageName(), R.layout.textnotif);
		remote.setImageViewResource(R.id.image, R.drawable.icon);
		remote.setTextViewText(R.id.headline, headline);
		remote.setTextViewText(R.id.status, text);
		thenotif.contentView = remote;
		
		mNotificationManager.notify(thenotifid, thenotif);
	}
	
	private void update_notif(int n, int total)
	{
		NotificationManager mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		
		remote = new RemoteViews(getPackageName(), R.layout.progressnotif);
		remote.setImageViewResource(R.id.image, R.drawable.icon);
		remote.setTextViewText(R.id.headline, headline);
		remote.setProgressBar(R.id.status, total, n, false);
		thenotif.contentView = remote;
		
		mNotificationManager.notify(thenotifid, thenotif);
	}
	
	@Override
	public void run()
	{
		Looper.prepare();
		
		Log.e("Dumload.Uploader[thread]", "This brought to you from the new thread.");
		
		set_up_notif("Dumload upload in progress");
		
		try {
			say("Uploading "+(Integer.toString(is.available()))+" bytes");
		
			update_notif("Connecting...");
		
			JSch jsch = new JSch();
			jsch.setKnownHosts(homedir + "/known_hosts");
			Session s = jsch.getSession("joshua", "nyus.joshuawise.com", 22);
			s.setUserInfo(this);
			s.connect();
			
			Channel channel = s.openChannel("exec");
			((ChannelExec)channel).setCommand("scp -t /tmp/lol.jpg");
			channel.connect();
			
			OutputStream scp_out = channel.getOutputStream();
			InputStream scp_in = channel.getInputStream();
			
			update_notif("Starting send...");
			
			/* Okay, BS out of the way.  Now go send the file. */
			expect_ack(scp_in);
			
			scp_out.write(("C0644 " + (Integer.toString(is.available())) + " lol.jpg\n").getBytes());
			scp_out.flush();
			
			expect_ack(scp_in);
			
			int total, nbytes;
			total = is.available();
			nbytes = 0;
			int len;
			byte[] buf = new byte[4096];
			while ((len = is.read(buf, 0, buf.length)) > 0)
			{
				scp_out.write(buf, 0, len);
				nbytes += len;
				update_notif(nbytes, total);
			}
			
			is.close();
			
			update_notif("Finishing file transfer...");
			
			scp_out.write("\0".getBytes());
			scp_out.flush();
			
			expect_ack(scp_in);
			
			channel.disconnect();
			
			update_notif("Preparing to resize image...");
			
			channel = s.openChannel("exec");
			((ChannelExec)channel).setCommand("pscale /tmp/lol.jpg");
			channel.connect();
			
			scp_in = channel.getInputStream();
			
			update_notif("Resizing image...");
			while ((len = scp_in.read(buf, 0, buf.length)) > 0)
				;
			
			channel.disconnect();
			update_notif("Upload complete.");
			
			sayNullNotification("Dumload upload complete", "Upload complete", "Dumload has finished uploading your file.");

			s.disconnect();
		} catch (Exception e) {
			Log.e("Dumload.uploader[thread]", "JSchException: "+(e.toString()));
			sayNullNotification("Dumload upload failed", "Upload failed", e.toString());
		}
		
		destroy_notif();
		
		Log.e("Dumload.uploader[thread]", "And now I'm back to life!");
	}
	
	private void say(String s) {
		Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
	}
	
	@Override
	public void onStart(Intent i, int startId)
	{
		uri = i.getData();
		homedir = getApplicationContext().getFilesDir().getAbsolutePath();
		int shits = 0;
		
		super.onStart(i, startId);
		
		Log.e("Dumload.Uploader", "Started.");
		Log.e("Dumload.Uploader", "My path is "+homedir);
		
		try {
			is = getContentResolver().openInputStream(uri);
		} catch (Exception e) {
			say("Failed to open input file.");
			return;
		}
		
		
		me = new Thread(this, "Uploader thread");
		me.start();
	}
	
	@Override
	public IBinder onBind(Intent i) {
		Log.e("Dumload.Uploader", "bound");
		
		return null;
	}
}
