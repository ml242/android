package ng.prk.prkngandroid.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.format.DateUtils;

import java.util.Calendar;
import java.util.Date;

import ng.prk.prkngandroid.Const;
import ng.prk.prkngandroid.io.ApiClient;
import ng.prk.prkngandroid.model.CheckinData;
import ng.prk.prkngandroid.receiver.CheckinMementoReceiver;

public class CheckinHelper {
    private final static String TAG = "NotifyUtils";

    public static void checkin(Context context, CheckinData checkin, String address, long remainingTime) {
        final PrkngPrefs prefs = PrkngPrefs.getInstance(context);
        final long endAt = System.currentTimeMillis() + remainingTime;
        if (CalendarUtils.isWeekLongDuration(remainingTime)) {
            prefs.setCheckin(checkin, address);
        } else {
            prefs.setCheckin(checkin, address, endAt);
            // Set alarm 30 min before expiry
            setAlarm(context,
                    endAt - Const.NotifationConfig.EXPIRY,
                    Const.NotificationTypes.EXPIRY);

            if (hasSmartReminder(endAt)) {
                prefs.setSmartReminder(prefs.hasNotifications());
                // Set smart alarm the night before a morning checkout
                setAlarm(context,
                        getSmartReminderTime(endAt),
                        Const.NotificationTypes.SMART_REMINDER);
            } else {
                prefs.setSmartReminder(false);
            }
        }

        checkin.setAddress(address);
        checkin.setCheckoutAt(new Date(endAt));
        NotifyUtils.notifyCheckinStart(context, checkin);
    }


    public static boolean checkout(Context context, long id) {
        try {
            final PrkngPrefs prefs = PrkngPrefs.getInstance(context);
            final String apiKey = prefs.getApiKey();

            prefs.setCheckout(id);
            NotifyUtils.removeNotifications(context);

            ApiClient.checkout(ApiClient.getService(), apiKey, id, null);

            context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

            removeAlarm(context, Const.NotificationTypes.SMART_REMINDER);
            removeAlarm(context, Const.NotificationTypes.EXPIRY);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }


    private static void setAlarm(Context context, long endAt, int type) {
        final PendingIntent pendingIntent =
                PendingIntent.getBroadcast(context,
                        Const.RequestCodes.CHECKIN_REMINDER,
                        CheckinMementoReceiver.newIntent(context, type),
                        PendingIntent.FLAG_CANCEL_CURRENT);

        final AlarmManager am =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (Const.NotificationTypes.SMART_REMINDER == type) {
            // The smart reminder doesn't need to be precise
            am.set(AlarmManager.RTC_WAKEUP,
                    endAt,
                    pendingIntent);
        } else {
            AlarmManagerCompat.setExact(am,
                    AlarmManager.RTC_WAKEUP,
                    endAt,
                    pendingIntent);
        }
    }

    private static void removeAlarm(Context context, int type) {
        final PendingIntent pendingIntent =
                PendingIntent.getBroadcast(context,
                        Const.RequestCodes.CHECKIN_REMINDER,
                        CheckinMementoReceiver.newIntent(context, type),
                        PendingIntent.FLAG_CANCEL_CURRENT);
        final AlarmManager am =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pendingIntent);
    }

    public static boolean hasSmartReminder(long endAt) {
        if (Long.valueOf(Const.UNKNOWN_VALUE).equals(endAt)) {
            return false;
        }

        final Calendar calendarEnd = Calendar.getInstance();
        calendarEnd.setTimeInMillis(endAt);

        if (calendarEnd.get(Calendar.HOUR_OF_DAY) < 12) {
            // Smart reminder only for intervals ending before Noon
            final Calendar calendarNow = Calendar.getInstance();

            if (calendarEnd.before(calendarNow)) {
                // Shouldn't happen
                return false;
            } else if (calendarNow.get(Calendar.DAY_OF_YEAR) != calendarEnd.get(Calendar.DAY_OF_YEAR)) {
                // Not on the same day
                if (calendarNow.get(Calendar.HOUR_OF_DAY) < 16) {
                    // Now is before 4pm
                    return true;
                }
                if (calendarEnd.getTimeInMillis() - calendarNow.getTimeInMillis() >=
                        DateUtils.DAY_IN_MILLIS) {
                    // More than 24 hours
                    return true;
                }
            }

        }

        return false;
    }

    private static long getSmartReminderTime(long time) {
        final Calendar calendar = Calendar.getInstance();
        // Set calendar to the previous day;
        calendar.setTimeInMillis(time - Const.NotifationConfig.SMART_DAY_OFFSET);

        // Set clock to 20:00
        calendar.set(Calendar.HOUR_OF_DAY, Const.NotifationConfig.SMART_HOUR_OF_DAY);
        calendar.set(Calendar.MINUTE, Const.NotifationConfig.SMART_MINUTE);

        // return new time
        return calendar.getTimeInMillis();
    }
}
