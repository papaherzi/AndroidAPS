package info.nightscout.androidaps.plugins.general.overview;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

import androidx.arch.core.util.Function;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.careportal.CareportalFragment;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSettingsStatus;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.SetWarnColor;

class StatuslightHandler {

    boolean extended = false;
    /**
     * applies the statuslight subview on the overview fragement
     */
    void statuslight(TextView cageView, TextView iageView, TextView reservoirView,
                     TextView sageView, TextView batteryView) {
        PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();

        applyStatuslight("cage", CareportalEvent.SITECHANGE, cageView, extended ? (MainApp.getDbHelper().getLastCareportalEvent(CareportalEvent.SITECHANGE).age(true) + " ") : "", 48, 72);
        applyStatuslight("iage", CareportalEvent.INSULINCHANGE, iageView, extended ? (MainApp.getDbHelper().getLastCareportalEvent(CareportalEvent.INSULINCHANGE).age(true) + " ") : "", 72, 96);

        double reservoirLevel = pump.isInitialized() ? pump.getReservoirLevel() : -1;
        applyStatuslightLevel(R.string.key_statuslights_res_critical, 20.0,
                R.string.key_statuslights_res_warning, 50.0, reservoirView, "", reservoirLevel);
        reservoirView.setText(extended ? (DecimalFormatter.to0Decimal(reservoirLevel) + "U  ") : "");

        applyStatuslight("sage", CareportalEvent.SENSORCHANGE, sageView, extended ? (MainApp.getDbHelper().getLastCareportalEvent(CareportalEvent.SENSORCHANGE).age(true) + " ") : "", 164, 166);

        if (pump.model() != PumpType.AccuChekCombo && pump.model() != PumpType.DanaRS) {
            double batteryLevel = pump.isInitialized() ? pump.getBatteryLevel() : -1;
            applyStatuslightLevel(R.string.key_statuslights_bat_critical, 26.0,
                    R.string.key_statuslights_bat_warning, 51.0,
                    batteryView, "", batteryLevel);
            batteryView.setText(extended ? (DecimalFormatter.to0Decimal(batteryLevel) + "%  ") : "");

        } else {
            applyStatuslight("bage", CareportalEvent.PUMPBATTERYCHANGE, batteryView, extended ? (MainApp.getDbHelper().getLastCareportalEvent(CareportalEvent.PUMPBATTERYCHANGE).age(true) + " ") : "", 504, 240);
        }

    }

    void applyStatuslight(String nsSettingPlugin, String eventName, TextView view, String text,
                          int defaultWarnThreshold, int defaultUrgentThreshold) {
        NSSettingsStatus nsSettings = NSSettingsStatus.getInstance();

        if (view != null) {
            double urgent = nsSettings.getExtendedWarnValue(nsSettingPlugin, "urgent", defaultUrgentThreshold);
            double warn = nsSettings.getExtendedWarnValue(nsSettingPlugin, "warn", defaultWarnThreshold);
            CareportalEvent event = MainApp.getDbHelper().getLastCareportalEvent(eventName);
            double age = event != null ? event.getHoursFromStart() : Double.MAX_VALUE;
            applyStatuslight(view, text, age, warn, urgent, Double.MAX_VALUE, true);
        }
    }

    void applyStatuslightLevel(int criticalSetting, double criticalDefaultValue,
                               int warnSetting, double warnDefaultValue,
                               TextView view, String text, double level) {
        if (view != null) {
            double resUrgent = SP.getDouble(criticalSetting, criticalDefaultValue);
            double resWarn = SP.getDouble(warnSetting, warnDefaultValue);
            applyStatuslight(view, text, level, resWarn, resUrgent, -1, false);
        }
    }

    void applyStatuslight(TextView view, String text, double value, double warnThreshold,
                          double urgentThreshold, double invalid, boolean checkAscending) {
        Function<Double, Boolean> check = checkAscending ? (Double threshold) -> value >= threshold :
                (Double threshold) -> value <= threshold;
        if (value != invalid) {
            view.setText(text);
//            view.setBackgroundColor(MainApp.gc(R.color.transparent));
            if (check.apply(urgentThreshold)) {
                view.setTextColor(MainApp.gc(R.color.color_white));
                Drawable drawable = view.getBackground();
                drawable.setColorFilter(new PorterDuffColorFilter(0xff911d10, PorterDuff.Mode.SRC_IN));
            } else if (check.apply(warnThreshold)) {
                view.setTextColor(MainApp.gc(R.color.color_white));
                Drawable drawable = view.getBackground();
                drawable.setColorFilter(new PorterDuffColorFilter(0xfff0a30a, PorterDuff.Mode.SRC_IN));
            } else {
                view.setTextColor(MainApp.gc(R.color.color_white));
                Drawable drawable = view.getBackground();
                drawable.setColorFilter(new PorterDuffColorFilter(0xff3f4142, PorterDuff.Mode.SRC_IN));
            }
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }

    }

    /**
     * applies the extended statuslight subview on the overview fragement
     */
    void extendedStatuslight(TextView cageView, TextView iageView,
                             TextView reservoirView, TextView sageView,
                             TextView batteryView) {

        extended = true;
        statuslight(cageView, iageView, reservoirView, sageView, batteryView);
    }
}