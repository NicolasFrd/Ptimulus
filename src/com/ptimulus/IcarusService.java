package com.ptimulus;

import com.ptimulus.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.os.PowerManager;

public class IcarusService extends Service implements
		OnSharedPreferenceChangeListener {

	public static void activateIfNecessary(Context ctx) {
		if (isEnabled(ctx)) {
			Intent startIntent = new Intent(ctx, IcarusService.class);
			ctx.startService(startIntent);
		}
	}

	boolean active;

	public IcarusService() {
		super();
		active = false;
	}

	IcarusApplication getIcarusApplication() {
		return (IcarusApplication) getApplicationContext();
	}

	static boolean isEnabled(Context ctx) {
		return ((IcarusApplication) ctx.getApplicationContext())
				.getIcarusPreferences().getBoolean("enableLogging", false);
	}

	static Notification updateNotification(Context ctx, boolean enabled) {
		NotificationManager notificationManager = (NotificationManager) ctx
				.getSystemService(Context.NOTIFICATION_SERVICE);

		if (enabled) {
			Notification n = new Notification(R.drawable.icaruslogo,
					"Icarus Active", System.currentTimeMillis());

			n.flags |= Notification.FLAG_ONGOING_EVENT;
			Intent ni = new Intent(ctx, IcarusManager.class);

			PendingIntent pi = PendingIntent.getActivity(ctx, 0, ni, 0);
			n.setLatestEventInfo(ctx, "Icarus", "Icarus Active", pi);
			notificationManager.notify(IcarusApplication.NOTIFY_ICARUS_ACTIVE,
					n);

			return n;
		} else {
			notificationManager.cancel(IcarusApplication.NOTIFY_ICARUS_ACTIVE);
			return null;
		}
	}

	public void start(Context ctx) {
		if (active)
			return;
		ds.start();
		logger.startLogging();
		wl.acquire();
		active = true;

		MediaPlayer mp = MediaPlayer.create(ctx, R.raw.icarusactivated);
		mp.start();
	}

	public void stop() {
		if (!active)
			return;
		ds.stop();
		logger.stopLogging();
		wl.release();
		active = false;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	private IcarusLogger logger;
	private PowerManager pm;
	private PowerManager.WakeLock wl;
	private DataSource ds;

	public void logDataEvent(String name, String data, long ts,
			boolean hasService) {
		logger.logDataEvent(name, data, ts, hasService);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		logger = new IcarusLogger(getIcarusApplication());

		getIcarusApplication().getDataSource().addDataListener(logger);

		pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IcarusMission");
		ds = getIcarusApplication().getDataSource();

		start(this);
		getIcarusApplication().getIcarusPreferences()
				.registerOnSharedPreferenceChangeListener(this);
		
		// The API for making a service run in the foreground changed between
		// API versions 4 and 5. For it to be effective, we must use the call
		// corresponding to the API version of the device that we're running on.

		// This code is for API versions 5 and later to make the service
		// ineligible for killing:
		Notification n = updateNotification(this, true);
		startForeground(IcarusApplication.NOTIFY_ICARUS_ACTIVE, n);

		// This code is for API versions 4 and earlier to make the service
		// ineligible for killing:
		
		//updateNotification(this, true);
		//setForeground(true);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		stop();
		getIcarusApplication().getIcarusPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);
		updateNotification(this, false);
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (!isEnabled(this))
			stopSelf();

	}
}
