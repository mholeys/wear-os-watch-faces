package uk.co.mholeys.dataface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import androidx.core.content.ContextCompat;

import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptionsExtension;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.fitness.result.DataReadResult;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 * <p>
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class DataFace1 extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. Defaults to one second
     * because the watch face needs to update seconds in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<DataFace1.Engine> mWeakReference;

        public EngineHandler(DataFace1.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            DataFace1.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private float mXOffset;
        private float mYOffset;
        private float mXCenter;
        private float mYCenter;
        private Paint mBackgroundPaint;
        private Paint mTimePaint;
        private Paint mTimeSecondsPaint;
        private Paint mDatePaint;
        private Paint mBatteryTextPaint, mBatteryPaint, mBatteryLowPaint;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
        private boolean mAmbient;
        private Paint dRedPaint, dGreenPaint, dBluePaint, dCyanPaint, dMagentaPaint, dPaint, dWhitePaint;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(DataFace1.this)
                    .setAcceptsTapEvents(true)
                    .build());

            mCalendar = Calendar.getInstance();

            Resources resources = DataFace1.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            // Initializes background.
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.background));


            // Initializes Watch Face.
            mTimePaint = new Paint();
            mTimePaint.setTypeface(NORMAL_TYPEFACE);
            mTimePaint.setAntiAlias(true);
            mTimePaint.setColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.digital_text));

            mTimeSecondsPaint = new Paint();
            mTimeSecondsPaint.setTypeface(NORMAL_TYPEFACE);
            mTimeSecondsPaint.setAntiAlias(true);
            mTimeSecondsPaint.setColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.digital_text));

            mDatePaint = new Paint();
            mDatePaint.setTypeface(NORMAL_TYPEFACE);
            mDatePaint.setAntiAlias(true);
            mDatePaint.setColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.digital_text));

            mBatteryTextPaint = new Paint();
            mBatteryTextPaint.setTypeface(NORMAL_TYPEFACE);
            mBatteryTextPaint.setAntiAlias(true);
            mBatteryTextPaint.setColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.digital_text));

            mBatteryPaint = new Paint();
            mBatteryPaint.setAntiAlias(true);
            mBatteryPaint.setStrokeWidth(1f);
            mBatteryPaint.setStyle(Paint.Style.STROKE);
            mBatteryPaint.setColor(Color.WHITE);

            mBatteryLowPaint = new Paint();
            mBatteryLowPaint.setAntiAlias(true);
            mBatteryLowPaint.setStrokeWidth(1f);
            mBatteryLowPaint.setStyle(Paint.Style.STROKE);
            mBatteryLowPaint.setColor(Color.RED);

            // Initialize debug paints
            dRedPaint = new Paint();
            dRedPaint.setColor(Color.RED);
            dRedPaint.setTypeface(NORMAL_TYPEFACE);
            dRedPaint.setAntiAlias(true);
            dRedPaint.setStrokeWidth(3f);
            dRedPaint.setStyle(Paint.Style.STROKE);

            dGreenPaint = new Paint();
            dGreenPaint.setColor(Color.GREEN);
            dGreenPaint.setTypeface(NORMAL_TYPEFACE);
            dGreenPaint.setAntiAlias(true);
            dGreenPaint.setStrokeWidth(3f);
            dGreenPaint.setStyle(Paint.Style.STROKE);

            dBluePaint = new Paint();
            dBluePaint.setColor(Color.BLUE);
            dBluePaint.setTypeface(NORMAL_TYPEFACE);
            dBluePaint.setAntiAlias(true);
            dBluePaint.setStrokeWidth(3f);
            dBluePaint.setStyle(Paint.Style.STROKE);

            dCyanPaint = new Paint();
            dCyanPaint.setColor(Color.CYAN);
            dCyanPaint.setTypeface(NORMAL_TYPEFACE);
            dCyanPaint.setAntiAlias(true);
            dCyanPaint.setStrokeWidth(3f);
            dCyanPaint.setStyle(Paint.Style.STROKE);

            dMagentaPaint = new Paint();
            dMagentaPaint.setColor(Color.MAGENTA);
            dMagentaPaint.setTypeface(NORMAL_TYPEFACE);
            dMagentaPaint.setAntiAlias(true);
            dMagentaPaint.setStrokeWidth(3f);
            dMagentaPaint.setStyle(Paint.Style.STROKE);
            dPaint = dMagentaPaint;

            dWhitePaint = new Paint();
            dWhitePaint.setColor(Color.WHITE);
            dWhitePaint.setTypeface(NORMAL_TYPEFACE);
            dWhitePaint.setAntiAlias(true);
            dWhitePaint.setStrokeWidth(3f);
            dWhitePaint.setStyle(Paint.Style.STROKE);

        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            DataFace1.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            DataFace1.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = DataFace1.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float textSecondsSize = resources.getDimension(isRound ? R.dimen.digital_text_seconds_size_round : R.dimen.digital_text_seconds_size);
            float textBatterySize = resources.getDimension(isRound ? R.dimen.digital_text_battery_size_round : R.dimen.digital_text_battery_size);

            mDatePaint.setTextSize(resources.getDimension(isRound ? R.dimen.date_text_size_round : R.dimen.date_text_size));

            mTimePaint.setTextSize(textSize);
            mTimeSecondsPaint.setTextSize(textSecondsSize);
            mBatteryTextPaint.setTextSize(textBatterySize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            mAmbient = inAmbientMode;
            if (mLowBitAmbient) {
                mTimePaint.setAntiAlias(!inAmbientMode);
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
//                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
//                            .show();
//                    Toast.makeText(getApplicationContext(), mTimePaint.getTextSize() + "", Toast.LENGTH_SHORT)
//                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            mXCenter = bounds.width() / 2f;
            mYCenter = bounds.height() / 2f;

            /*// Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);*/

            drawTime(canvas, bounds);
            drawDate(canvas, bounds);
            drawFitness(canvas, bounds);
            drawBattery(canvas, bounds);
            drawPhoneBattery(canvas, bounds);


//            String text = mAmbient
//                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
//                    mCalendar.get(Calendar.MINUTE))
//                    : String.format("%d:%02d:%02d", mCalendar.get(Calendar.HOUR),
//                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));
//            canvas.drawText(text, mXOffset, mYOffset, mTimePaint);
//            drawDebug(canvas, bounds);
        }

        private void drawDebug(Canvas canvas, Rect bounds) {
            if (!mAmbient) {
                Rect offset = new Rect();
                offset.left = (int) mXOffset;
                offset.top = (int) mYOffset;
                offset.bottom = bounds.bottom;
                offset.right = bounds.right;

                canvas.drawPoint(mXOffset, mYOffset, dPaint);

//                canvas.drawRect(offset, dRedPaint);
//                canvas.drawRect(bounds, dGreenPaint);

                canvas.drawPoint(mXCenter, mYOffset, dBluePaint);
            }
        }

        private void drawTime(Canvas canvas, Rect bounds) {
            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            int numSeconds = mCalendar.get(Calendar.SECOND);
            String hourMins = String.format(Locale.getDefault(), "%02d:%02d", mCalendar.get(Calendar.HOUR_OF_DAY), mCalendar.get(Calendar.MINUTE));
            String seconds = String.format(Locale.getDefault(), ":%02d", numSeconds);

            float timeWidth = mDatePaint.measureText(hourMins);
            Rect timeRect = new Rect();
            mTimePaint.getTextBounds(hourMins, 0, hourMins.length(), timeRect);
            float timeXPos = mXCenter - timeRect.width()/2f;
            canvas.drawText(hourMins, timeXPos, mYOffset, mTimePaint);
            if (!mAmbient) {
                canvas.drawText(seconds, timeXPos + timeRect.width() + 5, mYOffset, mTimeSecondsPaint);

                // Draw quarterly progress bar
                Paint quarterlyTimePaint = new Paint();
                quarterlyTimePaint.setColor(Color.WHITE);
                Rect quarterlyRect = new Rect();
                quarterlyRect.left = (int) timeXPos;
                int quart = (int) Math.ceil(numSeconds / 15f); // Calculate Current minute quarter
                // Calculate the width of the bar based on the current quarter and
                // the width of the hour/min text
                quarterlyRect.right = (int) timeXPos + quart * ((timeRect.width() + 8) / 4);
                quarterlyRect.top = (int) (mYOffset + 5);
                quarterlyRect.bottom = (int) (mYOffset + 10);
                canvas.drawRect(quarterlyRect, quarterlyTimePaint);
            }
        }

        private void drawDate(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            String text = new SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(mCalendar.getTime());

            float dateWidth = mDatePaint.measureText(text);
            canvas.drawText(text, mXCenter - dateWidth/2f, mYOffset + 15 + mDatePaint.getTextSize(), mDatePaint);

        }

        private void drawFitness(Canvas canvas, Rect bounds) {

        }

        private void drawBattery(Canvas canvas, Rect bounds) {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = getApplicationContext().registerReceiver(null, ifilter);

            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;

            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            float batteryPct = level / (float)scale;
            Paint batteryPaint = batteryPct < 0.25f ? mBatteryLowPaint : mBatteryPaint;

            drawCircular(canvas, bounds,
                    bounds.width() / 4f,
                    mYOffset + mBatteryTextPaint.getTextSize() + 55f,
                    70f,
                    batteryPct,
                    batteryPaint);

            // How are we charging?
            int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
            boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;
            boolean discharge = chargePlug == BatteryManager.BATTERY_STATUS_DISCHARGING;
            Resources resources = DataFace1.this.getResources();

//            if (discharge) {
                String batteryLevel = (int) (batteryPct * 100) + "%";

                float percentageWidth = mBatteryTextPaint.measureText(batteryLevel);
                canvas.drawText(batteryLevel,
                        bounds.width() / 4f - percentageWidth / 2f,
                        mYOffset + mBatteryTextPaint.getTextSize() + 55f + 32f + mBatteryTextPaint.getTextSize() / 2f,
                        mBatteryTextPaint);
//            } else if (usbCharge) {
//                // TODO: draw drawables
//                Drawable d = getResources().getDrawable(R.drawable.ic_battery_charging_20, getTheme());
//                d.setBounds((int) (bounds.width() / 4f),
//                        (int)mYOffset + 55 + 32,
//                        5,
//                        5);
//                d.draw(canvas);
//            } else if (acCharge) {
//                Drawable d = getResources().getDrawable(R.drawable.ic_battery_charging_full, getTheme());
//                d.setBounds((int) (bounds.width() / 4f),
//                        (int)mYOffset + 55 + 32,
//                        5,
//                        5);
//                d.draw(canvas);
//                // TODO: draw drawables
//            }

        }

        private void drawPhoneBattery(Canvas canvas, Rect bounds) {
        }


        /**
         *
         * @param canvas canvas of draw
         * @param bounds bounds of draw
         * @param x Centre of the circle
         * @param y Top of the circle
         * @param size Width/height of the circle
         * @param percentage total percentage of the circle
         * @param paint paint to use
         */
        private void drawCircular(Canvas canvas, Rect bounds, float x, float y, float size, float percentage, Paint paint) {
            RectF r1 = new RectF();
            r1.left = x - size/2f;
            r1.right = r1.left + size;
            r1.top = y;
            r1.bottom = r1.top + size;

            canvas.drawArc(r1, 0, (percentage * 360), false, paint);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
