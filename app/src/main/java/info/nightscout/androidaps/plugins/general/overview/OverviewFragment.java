package info.nightscout.androidaps.plugins.general.overview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jjoe64.graphview.GraphView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.QuickWizard;
import info.nightscout.androidaps.data.QuickWizardEntry;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.dialogs.CalibrationDialog;
import info.nightscout.androidaps.dialogs.CarbsDialog;
import info.nightscout.androidaps.dialogs.InsulinDialog;
import info.nightscout.androidaps.dialogs.ProfileSwitchDialog;
import info.nightscout.androidaps.dialogs.ProfileViewerDialog;
import info.nightscout.androidaps.dialogs.TempTargetDialog;
import info.nightscout.androidaps.dialogs.TreatmentDialog;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.dialogs.WizardDialog;
import info.nightscout.androidaps.events.EventAcceptOpenLoopChange;
import info.nightscout.androidaps.events.EventCareportalEventChange;
import info.nightscout.androidaps.events.EventExtendedBolusChange;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventProfileNeedsUpdate;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTempTargetChange;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.aps.loop.APSResult;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.aps.loop.events.EventNewOpenLoopNotification;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.careportal.CareportalFragment;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSDeviceStatus;
import info.nightscout.androidaps.plugins.general.overview.activities.QuickWizardListActivity;
import info.nightscout.androidaps.plugins.general.overview.graphData.GraphData;
import info.nightscout.androidaps.plugins.general.wear.ActionStringHandler;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensData;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.CobInfo;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventIobCalculationProgress;
import info.nightscout.androidaps.plugins.source.SourceDexcomPlugin;
import info.nightscout.androidaps.plugins.source.SourceXdripPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.BolusWizard;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.DefaultValueHelper;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.OKDialog;
import info.nightscout.androidaps.utils.Profiler;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.SingleClickButton;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.ToastUtils;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

import static info.nightscout.androidaps.R.drawable.*;
import static info.nightscout.androidaps.utils.DateUtil.now;

public class OverviewFragment extends Fragment implements View.OnClickListener, View.OnLongClickListener {
    private static Logger log = LoggerFactory.getLogger(L.OVERVIEW);

    private CompositeDisposable disposable = new CompositeDisposable();

    TextView timeView;
    TextView bgView;
    TextView arrowView;
    TextView sensitivityView;
    TextView timeAgoView;
    TextView timeAgoShortView;
    TextView deltaView;
    TextView deltaShortView;
    TextView avgdeltaView;
    TextView baseBasalView;
    TextView extendedBolusView;
    LinearLayout extendedBolusLayout;
    TextView activeProfileView;
    TextView iobView;
    TextView cobView;
    TextView apsModeView;
    TextView tempTargetView;
    TextView pumpStatusView;
    TextView pumpDeviceStatusView;
    TextView openapsDeviceStatusView;
    TextView uploaderDeviceStatusView;
    TextView iobCalculationProgressView;
    LinearLayout loopStatusLayout;
    LinearLayout pumpStatusLayout;
    GraphView bgGraph;
    GraphView iobGraph;
    GraphView cobGraph;
    GraphView devGraph;
    ImageButton chartButton;

    TextView iage;
    TextView cage;
    TextView sage;;
    TextView pbage;

    TextView iageView;
    TextView cageView;
    TextView reservoirView;
    TextView sageView;
    TextView batteryView;
    LinearLayout statuslightsLayout;

    RecyclerView notificationsView;
    LinearLayoutManager llm;

    LinearLayout acceptTempLayout;
    SingleClickButton acceptTempButton;

    SingleClickButton treatmentButton;
    SingleClickButton wizardButton;
    SingleClickButton calibrationButton;
    SingleClickButton insulinButton;
    SingleClickButton carbsButton;
    SingleClickButton cgmButton;
    SingleClickButton quickWizardButton;

    //Loop Images
    ImageView ic_loop_white;
    ImageView ic_user_white;
    ImageView ic_zielkreuz_white;

    //BG Value Image
    ImageView ic_bg_value;

    //Spheren Images
    ImageView ic_delta_28;
    ImageView ic_clock_28;
    ImageView ic_pumpe_28;
    ImageView ic_carb_28;
    ImageView ic_bas_28;
    ImageView ic_as_28;

    //Statuslights Images
    ImageView ic_katheter_white;
    ImageView ic_cartridge_white;
    ImageView ic_libre_white;
    ImageView ic_battery_white;

    boolean smallWidth;
    boolean smallHeight;

    public static boolean shorttextmode = true;

    private boolean accepted;

    private int rangeToDisplay = 3; // for graph

    Handler sLoopHandler = new Handler();
    Runnable sRefreshLoop = null;

    public enum CHARTTYPE {PRE, BAS, IOB, COB, DEV, SEN, ACTPRIM, ACTSEC, DEVSLOPE, TREATM}

    private static final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledUpdate = null;

    public OverviewFragment() {
        super();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        //check screen width
        final DisplayMetrics dm = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
        int screen_width = dm.widthPixels;
        int screen_height = dm.heightPixels;
        smallWidth = screen_width <= Constants.SMALL_WIDTH;
        smallHeight = screen_height <= Constants.SMALL_HEIGHT;
        boolean landscape = screen_height < screen_width;

        View view;

        if (MainApp.sResources.getBoolean(R.bool.isTablet) && (Config.NSCLIENT)) {
            view = inflater.inflate(R.layout.overview_fragment_nsclient_tablet, container, false);
        } else if (Config.NSCLIENT) {
            view = inflater.inflate(R.layout.overview_fragment_nsclient, container, false);
            shorttextmode = true;
        } else if (smallHeight || landscape) {
            view = inflater.inflate(R.layout.overview_fragment_smallheight, container, false);
        } else {
            view = inflater.inflate(R.layout.overview_fragment, container, false);
        }

        //Loop Images
//        ic_loop_white = view.findViewById(R.id.ic_loop_white);
//        ic_user_white = view.findViewById(R.id.ic_user_white);
//        ic_zielkreuz_white = view.findViewById(R.id.ic_zielkreuz_white);

        //BG Value Image
//        ic_bg_value = view.findViewById(R.id.ic_bg_value);

        //Spheren Images
        ic_delta_28 = view.findViewById(R.id.ic_delta_28);
//        ic_clock_28 = view.findViewById(R.id.ic_clock_28 );
        ic_pumpe_28 = view.findViewById(R.id.ic_pumpe_28);
        ic_carb_28 = view.findViewById(R.id.ic_carb_28);
        ic_bas_28 = view.findViewById(R.id.ic_bas_28);
        ic_as_28 = view.findViewById(R.id.ic_as_28);
        //Statuslights Images
//        ic_katheter_white = view.findViewById(R.id.ic_katheter_white);
//        ic_cartridge_white = view.findViewById(R.id.ic_cartridge_white);
//        ic_libre_white = view.findViewById(R.id.ic_libre_white);
//        ic_battery_white = view.findViewById(R.id.ic_battery_white);

        timeView = (TextView) view.findViewById(R.id.overview_time);
        bgView = (TextView) view.findViewById(R.id.overview_bg);
        arrowView = (TextView) view.findViewById(R.id.overview_arrow);
        if (smallWidth) {
            arrowView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 35);
        }
        sensitivityView = (TextView) view.findViewById(R.id.overview_sensitivity);
        timeAgoView = (TextView) view.findViewById(R.id.overview_timeago);
        timeAgoShortView = (TextView) view.findViewById(R.id.overview_timeagoshort);
        deltaView = (TextView) view.findViewById(R.id.overview_delta);
        deltaShortView = (TextView) view.findViewById(R.id.overview_deltashort);
        avgdeltaView = (TextView) view.findViewById(R.id.overview_avgdelta);
        baseBasalView = (TextView) view.findViewById(R.id.overview_basebasal);
        extendedBolusView = (TextView) view.findViewById(R.id.overview_extendedbolus);
//        extendedBolusLayout = view.findViewById(R.id.overview_extendedbolus_layout);
        activeProfileView = (TextView) view.findViewById(R.id.overview_activeprofile);
        pumpStatusView = (TextView) view.findViewById(R.id.overview_pumpstatus);
        pumpDeviceStatusView = (TextView) view.findViewById(R.id.overview_pump);
        openapsDeviceStatusView = (TextView) view.findViewById(R.id.overview_openaps);
        uploaderDeviceStatusView = (TextView) view.findViewById(R.id.overview_uploader);
        iobCalculationProgressView = (TextView) view.findViewById(R.id.overview_iobcalculationprogess);
        loopStatusLayout = (LinearLayout) view.findViewById(R.id.overview_looplayout);
        pumpStatusLayout = (LinearLayout) view.findViewById(R.id.overview_pumpstatuslayout);

        pumpStatusView.setBackgroundColor(MainApp.gc(R.color.colorInitializingBorder));

        iobView = (TextView) view.findViewById(R.id.overview_iob);
        cobView = (TextView) view.findViewById(R.id.overview_cob);
        apsModeView = (TextView) view.findViewById(R.id.overview_apsmode);
        tempTargetView = (TextView) view.findViewById(R.id.overview_temptarget);

        iage = view.findViewById(R.id.careportal_insulinage);
        cage = view.findViewById(R.id.careportal_canulaage);
        sage = view.findViewById(R.id.careportal_sensorage);
        pbage = view.findViewById(R.id.careportal_pbage);

//        iageView = view.findViewById(R.id.overview_insulinage);
        cageView = view.findViewById(R.id.overview_canulaage);
        reservoirView = view.findViewById(R.id.overview_reservoirlevel);
        sageView = view.findViewById(R.id.overview_sensorage);
        batteryView = view.findViewById(R.id.overview_batterylevel);
        statuslightsLayout = view.findViewById(R.id.overview_statuslights);

        bgGraph = (GraphView) view.findViewById(R.id.overview_bggraph);
        iobGraph = (GraphView) view.findViewById(R.id.overview_iobgraph);
        cobGraph = (GraphView) view.findViewById(R.id.overview_cobgraph);
        devGraph = (GraphView) view.findViewById(R.id.overview_devgraph);

        treatmentButton = (SingleClickButton) view.findViewById(R.id.overview_treatmentbutton);
        treatmentButton.setOnClickListener(this);
        wizardButton = (SingleClickButton) view.findViewById(R.id.overview_wizardbutton);
        wizardButton.setOnClickListener(this);
        insulinButton = (SingleClickButton) view.findViewById(R.id.overview_insulinbutton);
        if (insulinButton != null)
            insulinButton.setOnClickListener(this);
        carbsButton = (SingleClickButton) view.findViewById(R.id.overview_carbsbutton);
        if (carbsButton != null)
            carbsButton.setOnClickListener(this);
        acceptTempButton = (SingleClickButton) view.findViewById(R.id.overview_accepttempbutton);
        if (acceptTempButton != null)
            acceptTempButton.setOnClickListener(this);
        quickWizardButton = (SingleClickButton) view.findViewById(R.id.overview_quickwizardbutton);
        quickWizardButton.setOnClickListener(this);
        quickWizardButton.setOnLongClickListener(this);
        calibrationButton = (SingleClickButton) view.findViewById(R.id.overview_calibrationbutton);
        if (calibrationButton != null)
            calibrationButton.setOnClickListener(this);
        cgmButton = (SingleClickButton) view.findViewById(R.id.overview_cgmbutton);
        if (cgmButton != null)
            cgmButton.setOnClickListener(this);

        acceptTempLayout = (LinearLayout) view.findViewById(R.id.overview_accepttemplayout);

        notificationsView = (RecyclerView) view.findViewById(R.id.overview_notifications);
        notificationsView.setHasFixedSize(false);
        llm = new LinearLayoutManager(view.getContext());
        notificationsView.setLayoutManager(llm);

        int axisWidth = 50;

        if (dm.densityDpi <= 120)
            axisWidth = 3;
        else if (dm.densityDpi <= 160)
            axisWidth = 10;
        else if (dm.densityDpi <= 320)
            axisWidth = 35;
        else if (dm.densityDpi <= 420)
            axisWidth = 50;
        else if (dm.densityDpi <= 560)
            axisWidth = 70;
        else
            axisWidth = 80;

        bgGraph.getGridLabelRenderer().setGridColor(MainApp.gc(R.color.graphgrid));
        bgGraph.getGridLabelRenderer().reloadStyles();
        bgGraph.getGridLabelRenderer().setLabelVerticalWidth(axisWidth);

        iobGraph.getGridLabelRenderer().setGridColor(MainApp.gc(R.color.graphgrid));
        iobGraph.getGridLabelRenderer().reloadStyles();
        iobGraph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        iobGraph.getGridLabelRenderer().setLabelVerticalWidth(axisWidth);
        iobGraph.getGridLabelRenderer().setNumVerticalLabels(3);

        cobGraph.getGridLabelRenderer().setGridColor(MainApp.gc(R.color.graphgrid));
        cobGraph.getGridLabelRenderer().reloadStyles();
        cobGraph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        cobGraph.getGridLabelRenderer().setLabelVerticalWidth(axisWidth);
        cobGraph.getGridLabelRenderer().setNumVerticalLabels(3);

        devGraph.getGridLabelRenderer().setGridColor(MainApp.gc(R.color.graphgrid));
        devGraph.getGridLabelRenderer().reloadStyles();
        devGraph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        devGraph.getGridLabelRenderer().setLabelVerticalWidth(axisWidth);
        devGraph.getGridLabelRenderer().setNumVerticalLabels(3);

        rangeToDisplay = SP.getInt(R.string.key_rangetodisplay, 3);

        bgGraph.setOnLongClickListener(v -> {
            rangeToDisplay = rangeToDisplay * 2;
            rangeToDisplay = rangeToDisplay > 24 ? 3 : rangeToDisplay;
            SP.putInt(R.string.key_rangetodisplay, rangeToDisplay);
            updateGUI("rangeChange");
            SP.putBoolean(R.string.key_objectiveusescale, true);
            return false;
        });

/*        bgGraph.setOnLongClickListener(v -> {
            rangeToDisplay += 6;
            rangeToDisplay = rangeToDisplay > 24 ? 6 : rangeToDisplay;
            SP.putInt(R.string.key_rangetodisplay, rangeToDisplay);
            updateGUI("rangeChange");
            SP.putBoolean(R.string.key_objectiveusescale, true);
            return false;
        });*/

        setupChartMenu(view);

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        disposable.clear();
        sLoopHandler.removeCallbacksAndMessages(null);
        unregisterForContextMenu(apsModeView);
        unregisterForContextMenu(activeProfileView);
        unregisterForContextMenu(tempTargetView);
    }

    @Override
    public void onResume() {
        super.onResume();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventRefreshOverview.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(eventOpenAPSUpdateGui -> scheduleUpdateGUI(eventOpenAPSUpdateGui.getFrom()),
                        FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventExtendedBolusChange.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> scheduleUpdateGUI("EventExtendedBolusChange"),
                        FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventTempBasalChange.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> scheduleUpdateGUI("EventTempBasalChange"),
                        FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventTreatmentChange.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> scheduleUpdateGUI("EventTreatmentChange"),
                        FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventTempTargetChange.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> scheduleUpdateGUI("EventTempTargetChange"),
                        FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventAcceptOpenLoopChange.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> scheduleUpdateGUI("EventAcceptOpenLoopChange"),
                        FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventCareportalEventChange.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> scheduleUpdateGUI("EventCareportalEventChange"),
                        FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventInitializationChanged.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> scheduleUpdateGUI("EventInitializationChanged"),
                        FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventAutosensCalculationFinished.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> scheduleUpdateGUI("EventAutosensCalculationFinished"),
                        FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventProfileNeedsUpdate.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> scheduleUpdateGUI("EventProfileNeedsUpdate"),
                        FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventPreferenceChange.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> scheduleUpdateGUI("EventPreferenceChange"),
                        FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventNewOpenLoopNotification.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> scheduleUpdateGUI("EventNewOpenLoopNotification"),
                        FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventPumpStatusChanged.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updatePumpStatus(event.getStatus()),
                        FabricPrivacy::logException
                ));
        disposable.add(RxBus.INSTANCE
                .toObservable(EventIobCalculationProgress.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> {
                            if (iobCalculationProgressView != null)
                                iobCalculationProgressView.setText(event.getProgress());
                        },
                        FabricPrivacy::logException
                ));
        sRefreshLoop = () -> {
            scheduleUpdateGUI("refreshLoop");
            sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
        };
        sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
        registerForContextMenu(apsModeView);
        registerForContextMenu(activeProfileView);
        registerForContextMenu(tempTargetView);
        updateGUI("onResume");
    }

    private void setupChartMenu(View view) {
        chartButton = (ImageButton) view.findViewById(R.id.overview_chartMenuButton);
        chartButton.setOnClickListener(v -> {
            final LoopPlugin.LastRun finalLastRun = LoopPlugin.lastRun;
            boolean predictionsAvailable;
            if (Config.APS)
                predictionsAvailable = finalLastRun != null && finalLastRun.request.hasPredictions;
            else if (Config.NSCLIENT)
                predictionsAvailable = true;
            else
                predictionsAvailable = false;

            MenuItem item, dividerItem;
            CharSequence title;
            int titleMaxChars = 0;
            SpannableString s;
            PopupMenu popup = new PopupMenu(v.getContext(), v);
            if (predictionsAvailable) {
                item = popup.getMenu().add(Menu.NONE, CHARTTYPE.PRE.ordinal(), Menu.NONE, "Predictions");
                title = item.getTitle();
                if (titleMaxChars < title.length()) titleMaxChars = title.length();
                s = new SpannableString(title);
                s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.prediction, null)), 0, s.length(), 0);
                item.setTitle(s);
                item.setCheckable(true);
                item.setChecked(SP.getBoolean("showprediction", true));
            }

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.BAS.ordinal(), Menu.NONE, MainApp.gs(R.string.overview_show_basals));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.basal, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(SP.getBoolean("showbasals", true));

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.ACTPRIM.ordinal(), Menu.NONE, MainApp.gs(R.string.overview_show_activity));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.activity, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(SP.getBoolean("showactivityprimary", true));

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.TREATM.ordinal(), Menu.NONE, MainApp.gs(R.string.overview_show_treatments));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.treatment, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(SP.getBoolean("showtreatments", true));

            dividerItem = popup.getMenu().add("");
            dividerItem.setEnabled(false);

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.IOB.ordinal(), Menu.NONE, MainApp.gs(R.string.overview_show_iob));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.iob, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(SP.getBoolean("showiob", true));

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.COB.ordinal(), Menu.NONE, MainApp.gs(R.string.overview_show_cob));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.cob, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(SP.getBoolean("showcob", true));

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.DEV.ordinal(), Menu.NONE, MainApp.gs(R.string.overview_show_deviations));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.deviations, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(SP.getBoolean("showdeviations", false));

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.SEN.ordinal(), Menu.NONE, MainApp.gs(R.string.overview_show_sensitivity));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.ratio, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(SP.getBoolean("showratios", false));

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.ACTSEC.ordinal(), Menu.NONE, MainApp.gs(R.string.overview_show_activity));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.activity, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(SP.getBoolean("showactivitysecondary", true));

            if (MainApp.devBranch) {
                item = popup.getMenu().add(Menu.NONE, CHARTTYPE.DEVSLOPE.ordinal(), Menu.NONE, "Deviation slope");
                title = item.getTitle();
                if (titleMaxChars < title.length()) titleMaxChars = title.length();
                s = new SpannableString(title);
                s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.devslopepos, null)), 0, s.length(), 0);
                item.setTitle(s);
                item.setCheckable(true);
                item.setChecked(SP.getBoolean("showdevslope", false));
            }

            // Fairly good guestimate for required divider text size...
            title = new String(new char[titleMaxChars + 10]).replace("\0", "_");
            dividerItem.setTitle(title);

            popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    if (item.getItemId() == CHARTTYPE.PRE.ordinal()) {
                        SP.putBoolean("showprediction", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.BAS.ordinal()) {
                        SP.putBoolean("showbasals", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.IOB.ordinal()) {
                        SP.putBoolean("showiob", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.COB.ordinal()) {
                        SP.putBoolean("showcob", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.DEV.ordinal()) {
                        SP.putBoolean("showdeviations", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.SEN.ordinal()) {
                        SP.putBoolean("showratios", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.ACTPRIM.ordinal()) {
                        SP.putBoolean("showactivityprimary", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.ACTSEC.ordinal()) {
                        SP.putBoolean("showactivitysecondary", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.DEVSLOPE.ordinal()) {
                        SP.putBoolean("showdevslope", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.TREATM.ordinal()) {
                        SP.putBoolean("showtreatments", !item.isChecked());
                    }
                    scheduleUpdateGUI("onGraphCheckboxesCheckedChanged");
                    return true;
                }
            });
            chartButton.setImageResource( ic_arrow_drop_up_white_24dp);
            popup.setOnDismissListener(new PopupMenu.OnDismissListener() {
                @Override
                public void onDismiss(PopupMenu menu) {
                    chartButton.setImageResource( ic_arrow_drop_down_white_24dp);
                }
            });
            popup.show();
        });
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v == apsModeView) {
            final LoopPlugin loopPlugin = LoopPlugin.getPlugin();
            final PumpDescription pumpDescription =
                    ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription();
            if (!ProfileFunctions.getInstance().isProfileValid("ContexMenuCreation"))
                return;
            menu.setHeaderTitle(MainApp.gs(R.string.loop));
            if (loopPlugin.isEnabled(PluginType.LOOP)) {
                menu.add(MainApp.gs(R.string.disableloop));
                if (!loopPlugin.isSuspended()) {
                    menu.add(MainApp.gs(R.string.suspendloopfor1h));
                    menu.add(MainApp.gs(R.string.suspendloopfor2h));
                    menu.add(MainApp.gs(R.string.suspendloopfor3h));
                    menu.add(MainApp.gs(R.string.suspendloopfor10h));
                } else {
                    if (!loopPlugin.isDisconnected()) {
                        menu.add(MainApp.gs(R.string.resume));
                    }
                }
            }

            if (!loopPlugin.isEnabled(PluginType.LOOP)) {
                menu.add(MainApp.gs(R.string.enableloop));
            }

            if (!loopPlugin.isDisconnected()) {
                showSuspendtPump(menu, pumpDescription);
            } else {
                menu.add(MainApp.gs(R.string.reconnect));
            }

        } else if (v == activeProfileView) {
            menu.setHeaderTitle(MainApp.gs(R.string.profile));
            menu.add(MainApp.gs(R.string.danar_viewprofile));
            if (ConfigBuilderPlugin.getPlugin().getActiveProfileInterface() != null && ConfigBuilderPlugin.getPlugin().getActiveProfileInterface().getProfile() != null) {
                menu.add(MainApp.gs(R.string.careportal_profileswitch));
            }
        } else if (v == tempTargetView) {
            menu.setHeaderTitle(MainApp.gs(R.string.careportal_temporarytarget));
            menu.add(MainApp.gs(R.string.custom));
            menu.add(MainApp.gs(R.string.eatingsoon));
            menu.add(MainApp.gs(R.string.activity));
            menu.add(MainApp.gs(R.string.hypo));
            if (TreatmentsPlugin.getPlugin().getTempTargetFromHistory() != null) {
                menu.add(MainApp.gs(R.string.cancel));
            }
        }
    }

    private void showSuspendtPump(ContextMenu menu, PumpDescription pumpDescription) {
        if (pumpDescription.tempDurationStep15mAllowed)
            menu.add(MainApp.gs(R.string.disconnectpumpfor15m));
        if (pumpDescription.tempDurationStep30mAllowed)
            menu.add(MainApp.gs(R.string.disconnectpumpfor30m));
        menu.add(MainApp.gs(R.string.disconnectpumpfor1h));
        menu.add(MainApp.gs(R.string.disconnectpumpfor2h));
        menu.add(MainApp.gs(R.string.disconnectpumpfor3h));
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final Profile profile = ProfileFunctions.getInstance().getProfile();
        if (profile == null)
            return true;
        final LoopPlugin loopPlugin = LoopPlugin.getPlugin();
        if (item.getTitle().equals(MainApp.gs(R.string.disableloop))) {
            loopPlugin.setPluginEnabled(PluginType.LOOP, false);
            loopPlugin.setFragmentVisible(PluginType.LOOP, false);
            ConfigBuilderPlugin.getPlugin().storeSettings("DisablingLoop");
            updateGUI("suspendmenu");
            ConfigBuilderPlugin.getPlugin().getCommandQueue().cancelTempBasal(true, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            NSUpload.uploadOpenAPSOffline(24 * 60); // upload 24h, we don't know real duration
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.enableloop))) {
            loopPlugin.setPluginEnabled(PluginType.LOOP, true);
            loopPlugin.setFragmentVisible(PluginType.LOOP, true);
            ConfigBuilderPlugin.getPlugin().storeSettings("EnablingLoop");
            updateGUI("suspendmenu");
            NSUpload.uploadOpenAPSOffline(0);
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.resume)) ||
                item.getTitle().equals(MainApp.gs(R.string.reconnect))) {
            loopPlugin.suspendTo(0L);
            updateGUI("suspendmenu");
            ConfigBuilderPlugin.getPlugin().getCommandQueue().cancelTempBasal(true, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            SP.putBoolean(R.string.key_objectiveusereconnect, true);
            NSUpload.uploadOpenAPSOffline(0);
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.suspendloopfor1h))) {
            LoopPlugin.getPlugin().suspendLoop(60);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.suspendloopfor2h))) {
            LoopPlugin.getPlugin().suspendLoop(120);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.suspendloopfor3h))) {
            LoopPlugin.getPlugin().suspendLoop(180);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.suspendloopfor10h))) {
            LoopPlugin.getPlugin().suspendLoop(600);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.disconnectpumpfor15m))) {
            LoopPlugin.getPlugin().disconnectPump(15, profile);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.disconnectpumpfor30m))) {
            LoopPlugin.getPlugin().disconnectPump(30, profile);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.disconnectpumpfor1h))) {
            LoopPlugin.getPlugin().disconnectPump(60, profile);
            SP.putBoolean(R.string.key_objectiveusedisconnect, true);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.disconnectpumpfor2h))) {
            LoopPlugin.getPlugin().disconnectPump(120, profile);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.disconnectpumpfor3h))) {
            LoopPlugin.getPlugin().disconnectPump(180, profile);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.careportal_profileswitch))) {
            FragmentManager manager = getFragmentManager();
            if (manager != null)
                new ProfileSwitchDialog().show(manager, "Overview");
        } else if (item.getTitle().equals(MainApp.gs(R.string.danar_viewprofile))) {
            Bundle args = new Bundle();
            args.putLong("time", DateUtil.now());
            args.putInt("mode", ProfileViewerDialog.Mode.RUNNING_PROFILE.ordinal());
            ProfileViewerDialog pvd = new ProfileViewerDialog();
            pvd.setArguments(args);
            FragmentManager manager = getFragmentManager();
            if (manager != null)
                pvd.show(manager, "ProfileViewDialog");
        } else if (item.getTitle().equals(MainApp.gs(R.string.eatingsoon))) {
            double target = Profile.toMgdl(DefaultValueHelper.determineEatingSoonTT(), ProfileFunctions.getSystemUnits());
            TempTarget tempTarget = new TempTarget()
                    .date(System.currentTimeMillis())
                    .duration(DefaultValueHelper.determineEatingSoonTTDuration())
                    .reason(MainApp.gs(R.string.eatingsoon))
                    .source(Source.USER)
                    .low(target)
                    .high(target);
            TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
        } else if (item.getTitle().equals(MainApp.gs(R.string.activity))) {
            double target = Profile.toMgdl(DefaultValueHelper.determineActivityTT(), ProfileFunctions.getSystemUnits());
            TempTarget tempTarget = new TempTarget()
                    .date(now())
                    .duration(DefaultValueHelper.determineActivityTTDuration())
                    .reason(MainApp.gs(R.string.activity))
                    .source(Source.USER)
                    .low(target)
                    .high(target);
            TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
        } else if (item.getTitle().equals(MainApp.gs(R.string.hypo))) {
            double target = Profile.toMgdl(DefaultValueHelper.determineHypoTT(), ProfileFunctions.getSystemUnits());
            TempTarget tempTarget = new TempTarget()
                    .date(now())
                    .duration(DefaultValueHelper.determineHypoTTDuration())
                    .reason(MainApp.gs(R.string.hypo))
                    .source(Source.USER)
                    .low(target)
                    .high(target);
            TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
        } else if (item.getTitle().equals(MainApp.gs(R.string.custom))) {
            FragmentManager manager = getFragmentManager();
            if (manager != null)
                new TempTargetDialog().show(manager, "Overview");
        } else if (item.getTitle().equals(MainApp.gs(R.string.cancel))) {
            TempTarget tempTarget = new TempTarget()
                    .source(Source.USER)
                    .date(now())
                    .duration(0)
                    .low(0)
                    .high(0);
            TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        boolean xdrip = SourceXdripPlugin.getPlugin().isEnabled(PluginType.BGSOURCE);
        boolean dexcom = SourceDexcomPlugin.INSTANCE.isEnabled(PluginType.BGSOURCE);

        FragmentManager manager = getFragmentManager();
        // try to fix  https://fabric.io/nightscout3/android/apps/info.nightscout.androidaps/issues/5aca7a1536c7b23527eb4be7?time=last-seven-days
        // https://stackoverflow.com/questions/14860239/checking-if-state-is-saved-before-committing-a-fragmenttransaction
        if (manager == null || manager.isStateSaved())
            return;
        switch (v.getId()) {
            case R.id.overview_accepttempbutton:
                onClickAcceptTemp();
                break;
            case R.id.overview_quickwizardbutton:
                onClickQuickwizard();
                break;
            case R.id.overview_wizardbutton:
                WizardDialog wizardDialog = new WizardDialog();
                wizardDialog.show(manager, "WizardDialog");
                break;
            case R.id.overview_calibrationbutton:
                if (xdrip) {
                    CalibrationDialog calibrationDialog = new CalibrationDialog();
                    calibrationDialog.show(manager, "CalibrationDialog");
                } else if (dexcom) {
                    try {
                        String packageName = SourceDexcomPlugin.INSTANCE.findDexcomPackageName();
                        if (packageName != null) {
                            Intent i = new Intent("com.dexcom.cgm.activities.MeterEntryActivity");
                            i.setPackage(packageName);
                            startActivity(i);
                        } else {
                            ToastUtils.showToastInUiThread(getActivity(), MainApp.gs(R.string.dexcom_app_not_installed));
                        }
                    } catch (ActivityNotFoundException e) {
                        ToastUtils.showToastInUiThread(getActivity(), MainApp.gs(R.string.g5appnotdetected));
                    }
                }
                break;
            case R.id.overview_cgmbutton:
                if (xdrip)
                    openCgmApp("com.eveningoutpost.dexdrip");
                else if (dexcom) {
                    String packageName = SourceDexcomPlugin.INSTANCE.findDexcomPackageName();
                    if (packageName != null) {
                        openCgmApp(packageName);
                    } else {
                        ToastUtils.showToastInUiThread(getActivity(), MainApp.gs(R.string.dexcom_app_not_installed));
                    }
                }
                break;
            case R.id.overview_treatmentbutton:
                new TreatmentDialog().show(manager, "Overview");
                break;
            case R.id.overview_insulinbutton:
                new InsulinDialog().show(manager, "Overview");
                break;
            case R.id.overview_carbsbutton:
                new CarbsDialog().show(manager, "Overview");
                break;
            case R.id.overview_pumpstatus:
                if (ConfigBuilderPlugin.getPlugin().getActivePump().isSuspended() || !ConfigBuilderPlugin.getPlugin().getActivePump().isInitialized())
                    ConfigBuilderPlugin.getPlugin().getCommandQueue().readStatus("RefreshClicked", null);
                break;
        }

    }

    public boolean openCgmApp(String packageName) {
        PackageManager packageManager = getContext().getPackageManager();
        try {
            Intent intent = packageManager.getLaunchIntentForPackage(packageName);
            if (intent == null) {
                throw new ActivityNotFoundException();
            }
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            getContext().startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            OKDialog.show(getContext(), "", MainApp.gs(R.string.error_starting_cgm));
            return false;
        }
    }

    @Override
    public boolean onLongClick(View v) {
        switch (v.getId()) {
            case R.id.overview_quickwizardbutton:
                Intent i = new Intent(v.getContext(), QuickWizardListActivity.class);
                startActivity(i);
                return true;
        }
        return false;
    }

    private void onClickAcceptTemp() {
        Profile profile = ProfileFunctions.getInstance().getProfile();
        Context context = getContext();

        if (context == null) return;

        if (LoopPlugin.getPlugin().isEnabled(PluginType.LOOP) && profile != null) {
            LoopPlugin.getPlugin().invoke("Accept temp button", false);
            final LoopPlugin.LastRun finalLastRun = LoopPlugin.lastRun;
            if (finalLastRun != null && finalLastRun.lastAPSRun != null && finalLastRun.constraintsProcessed.isChangeRequested()) {
                OKDialog.showConfirmation(context, MainApp.gs(R.string.pump_tempbasal_label), finalLastRun.constraintsProcessed.toSpanned(), () -> {
                    hideTempRecommendation();
                    clearNotification();
                    LoopPlugin.getPlugin().acceptChangeRequest();
                });
            }
        }
    }

    void onClickQuickwizard() {
        final BgReading actualBg = DatabaseHelper.actualBg();
        final Profile profile = ProfileFunctions.getInstance().getProfile();
        final String profileName = ProfileFunctions.getInstance().getProfileName();
        final PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();

        final QuickWizardEntry quickWizardEntry = QuickWizard.INSTANCE.getActive();
        if (quickWizardEntry != null && actualBg != null && profile != null && pump != null) {
            quickWizardButton.setVisibility(View.VISIBLE);
            final BolusWizard wizard = quickWizardEntry.doCalc(profile, profileName, actualBg, true);

            if (wizard.getCalculatedTotalInsulin() > 0d && quickWizardEntry.carbs() > 0d) {
                Integer carbsAfterConstraints = MainApp.getConstraintChecker().applyCarbsConstraints(new Constraint<>(quickWizardEntry.carbs())).value();

                if (Math.abs(wizard.getInsulinAfterConstraints() - wizard.getCalculatedTotalInsulin()) >= pump.getPumpDescription().pumpType.determineCorrectBolusStepSize(wizard.getInsulinAfterConstraints()) || !carbsAfterConstraints.equals(quickWizardEntry.carbs())) {
                    OKDialog.show(getContext(), MainApp.gs(R.string.treatmentdeliveryerror), MainApp.gs(R.string.constraints_violation) + "\n" + MainApp.gs(R.string.changeyourinput));
                    return;
                }

                wizard.confirmAndExecute(getContext());
            }
        }
    }

    private void hideTempRecommendation() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> {
                if (acceptTempLayout != null)
                    acceptTempLayout.setVisibility(View.GONE);
            });
    }

    private void clearNotification() {
        NotificationManager notificationManager =
                (NotificationManager) MainApp.instance().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(Constants.notificationID);

        ActionStringHandler.handleInitiate("cancelChangeRequest");
    }

    private void updatePumpStatus(String status) {
        if (!status.equals("")) {
            pumpStatusView.setText(status);
            pumpStatusLayout.setVisibility(View.VISIBLE);
            loopStatusLayout.setVisibility(View.GONE);
        } else {
            pumpStatusLayout.setVisibility(View.GONE);
            loopStatusLayout.setVisibility(View.VISIBLE);
        }
    }

    public void scheduleUpdateGUI(final String from) {
        class UpdateRunnable implements Runnable {
            public void run() {
                Activity activity = getActivity();
                if (activity != null)
                    activity.runOnUiThread(() -> {
                        updateGUI(from);
                        scheduledUpdate = null;
                    });
            }
        }
        // prepare task for execution in 400 msec
        // cancel waiting task to prevent multiple updates
        if (scheduledUpdate != null)
            scheduledUpdate.cancel(false);
        Runnable task = new UpdateRunnable();
        final int msec = 500;
        scheduledUpdate = worker.schedule(task, msec, TimeUnit.MILLISECONDS);
    }

    @SuppressLint("SetTextI18n")
    public void updateGUI(final String from) {
        if (L.isEnabled(L.OVERVIEW))
            log.debug("updateGUI entered from: " + from);
        final long updateGUIStart = System.currentTimeMillis();

        if (getActivity() == null)
            return;

        if (timeView != null) { //must not exists
            timeView.setText(DateUtil.timeString(new Date()));
        }

        OverviewPlugin.INSTANCE.getNotificationStore().updateNotifications(notificationsView);

        pumpStatusLayout.setVisibility(View.GONE);
        loopStatusLayout.setVisibility(View.GONE);

        if (!ProfileFunctions.getInstance().isProfileValid("Overview")) {
            pumpStatusView.setText(R.string.noprofileset);
            pumpStatusLayout.setVisibility(View.VISIBLE);
            return;
        }
        loopStatusLayout.setVisibility(View.VISIBLE);

        CareportalFragment.updateAge(getActivity(), sage, iage, cage, pbage);
        BgReading actualBG = DatabaseHelper.actualBg();
        BgReading lastBG = DatabaseHelper.lastBg();

        final PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();

        final Profile profile = ProfileFunctions.getInstance().getProfile();
        final String profileName = ProfileFunctions.getInstance().getProfileName();

        final String units = ProfileFunctions.getSystemUnits();
        final double lowLine = OverviewPlugin.INSTANCE.determineLowLine();
        final double highLine = OverviewPlugin.INSTANCE.determineHighLine();

        Constraint<Boolean> closedLoopEnabled = MainApp.getConstraintChecker().isClosedLoopAllowed();

        // open loop mode
        final LoopPlugin.LastRun finalLastRun = LoopPlugin.lastRun;
        if (Config.APS && pump.getPumpDescription().isTempBasalCapable) {
            apsModeView.setVisibility(View.VISIBLE);
            Drawable drawable = apsModeView.getBackground();
            drawable.setColorFilter(new PorterDuffColorFilter(0x00000000, PorterDuff.Mode.SRC_ATOP));
//            Drawable wrapDrawable = DrawableCompat.wrap(ic_loop_white.getDrawable());
//            DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.loopgreen));
//            DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
            apsModeView.setTextColor(MainApp.gc(R.color.white));
            final LoopPlugin loopPlugin = LoopPlugin.getPlugin();
            if (loopPlugin.isEnabled(PluginType.LOOP) && loopPlugin.isSuperBolus()) {
                drawable = apsModeView.getBackground();
                drawable.setColorFilter(new PorterDuffColorFilter(0xfff06b0a, PorterDuff.Mode.SRC_ATOP));
//                wrapDrawable = DrawableCompat.wrap( ic_loop_white.getDrawable() );
//                DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.rose));
//                DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
                apsModeView.setText(String.format(MainApp.gs(R.string.loopsuperbolusfor), loopPlugin.minutesToEndOfSuspend()));
                apsModeView.setTextColor(MainApp.gc(R.color.white));
            } else if (loopPlugin.isDisconnected()) {
                drawable = apsModeView.getBackground();
                drawable.setColorFilter(new PorterDuffColorFilter(0xfff06b0a, PorterDuff.Mode.SRC_ATOP));
//                wrapDrawable = DrawableCompat.wrap(ic_loop_white.getDrawable());
//                DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.rose));
//                DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
                apsModeView.setText(String.format(MainApp.gs(R.string.loopdisconnectedfor), loopPlugin.minutesToEndOfSuspend()));
                apsModeView.setTextColor(MainApp.gc(R.color.white));
            } else if (loopPlugin.isEnabled(PluginType.LOOP) && loopPlugin.isSuspended()) {
                drawable = apsModeView.getBackground();
                drawable.setColorFilter(new PorterDuffColorFilter(0xfff0a30a, PorterDuff.Mode.SRC_ATOP));
//                wrapDrawable = DrawableCompat.wrap(ic_loop_white.getDrawable());
//                DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.conncinity_amber));
//                DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
                apsModeView.setText(String.format(MainApp.gs(R.string.loopsuspendedfor), loopPlugin.minutesToEndOfSuspend()));
                apsModeView.setTextColor(MainApp.gc(R.color.white));
            } else if (pump.isSuspended()) {
                drawable = apsModeView.getBackground();
                drawable.setColorFilter(new PorterDuffColorFilter(0xfff0a30a, PorterDuff.Mode.SRC_ATOP));
//                wrapDrawable = DrawableCompat.wrap(ic_loop_white.getDrawable());
//                DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.conncinity_amber));
//                DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
                apsModeView.setText(MainApp.gs(R.string.pumpsuspended));
                apsModeView.setTextColor(MainApp.gc(R.color.white));
            } else if (loopPlugin.isEnabled(PluginType.LOOP)) {
                if (closedLoopEnabled.value()) {
                    apsModeView.setText(MainApp.gs(R.string.closedloop));
                } else {
                    apsModeView.setText(MainApp.gs(R.string.openloop));
                }
            } else {
                drawable = apsModeView.getBackground();
                drawable.setColorFilter(new PorterDuffColorFilter(0xfff06b0a, PorterDuff.Mode.SRC_ATOP));
                //               wrapDrawable = DrawableCompat.wrap(ic_loop_white.getDrawable());
                //               DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.rose));
                //               DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
                apsModeView.setText(MainApp.gs(R.string.disabledloop));
                apsModeView.setTextColor(MainApp.gc(R.color.white));
            }
        } else {
            apsModeView.setVisibility(View.GONE);
        }

        // active profil
        activeProfileView.setText(ProfileFunctions.getInstance().getProfileNameWithDuration());
        if (profile.getPercentage() != 100 || profile.getTimeshift() != 0) {
            Drawable drawable = activeProfileView.getBackground();
            drawable.setColorFilter(new PorterDuffColorFilter(0xfff0a30a, PorterDuff.Mode.SRC_ATOP));
//            Drawable wrapDrawable = DrawableCompat.wrap(ic_user_white.getDrawable());
//            DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.conncinity_amber));
//            DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
            activeProfileView.setTextColor(MainApp.gc(R.color.white));
        } else {
            Drawable drawable = activeProfileView.getBackground();
            drawable.setColorFilter(new PorterDuffColorFilter(0x00000000, PorterDuff.Mode.SRC_ATOP));
            //           Drawable wrapDrawable = DrawableCompat.wrap(ic_zielkreuz_white.getDrawable());
            //           DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.conncinity_amber));
            //           DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
            activeProfileView.setTextColor(MainApp.gc(R.color.white));
        }

        // temp target
        TempTarget tempTarget = TreatmentsPlugin.getPlugin().getTempTargetFromHistory();
        if (tempTarget != null) {
            Drawable drawable = tempTargetView.getBackground();
            drawable.setColorFilter(new PorterDuffColorFilter(0xfff0a30a, PorterDuff.Mode.SRC_ATOP));
//            Drawable wrapDrawable = DrawableCompat.wrap(ic_zielkreuz_white.getDrawable());
//            DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.conncinity_amber));
//            DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
            tempTargetView.setVisibility(View.VISIBLE);
            tempTargetView.setText(Profile.toTargetRangeString(tempTarget.low, tempTarget.high, Constants.MGDL, units) + " " + DateUtil.untilString(tempTarget.end()));
            tempTargetView.setTextColor(MainApp.gc(R.color.white));
        } else {
            Drawable drawable = tempTargetView.getBackground();
            drawable.setColorFilter(new PorterDuffColorFilter(0x00000000, PorterDuff.Mode.SRC_ATOP));
//            Drawable wrapDrawable = DrawableCompat.wrap(ic_zielkreuz_white.getDrawable());
//            DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.white));
//            DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
            tempTargetView.setText(Profile.toTargetRangeString(profile.getTargetLowMgdl(), profile.getTargetHighMgdl(), Constants.MGDL, units));
            tempTargetView.setVisibility(View.VISIBLE);
            tempTargetView.setTextColor(MainApp.gc(R.color.white));
        }
        // **** Temp button ****
        if (acceptTempLayout != null) {
            boolean showAcceptButton = !closedLoopEnabled.value(); // Open mode needed
            showAcceptButton = showAcceptButton && finalLastRun != null && finalLastRun.lastAPSRun != null; // aps result must exist
            showAcceptButton = showAcceptButton && (finalLastRun.lastOpenModeAccept == null || finalLastRun.lastOpenModeAccept.getTime() < finalLastRun.lastAPSRun.getTime()); // never accepted or before last result
            showAcceptButton = showAcceptButton && finalLastRun.constraintsProcessed.isChangeRequested(); // change is requested

            if (showAcceptButton && pump.isInitialized() && !pump.isSuspended() && LoopPlugin.getPlugin().isEnabled(PluginType.LOOP)) {
                acceptTempLayout.setVisibility(View.VISIBLE);
                acceptTempButton.setText(MainApp.gs(R.string.setbasalquestion) + "\n" + finalLastRun.constraintsProcessed);
            } else {
                acceptTempLayout.setVisibility(View.GONE);
            }
        }

        // **** Calibration & CGM buttons ****
        boolean xDripIsBgSource = SourceXdripPlugin.getPlugin().isEnabled(PluginType.BGSOURCE);
        boolean dexcomIsSource = SourceDexcomPlugin.INSTANCE.isEnabled(PluginType.BGSOURCE);
        boolean bgAvailable = DatabaseHelper.actualBg() != null;
        if (calibrationButton != null) {
            if ((xDripIsBgSource || dexcomIsSource) && bgAvailable && SP.getBoolean(R.string.key_show_calibration_button, true)) {
                calibrationButton.setVisibility(View.VISIBLE);
            } else {
                calibrationButton.setVisibility(View.GONE);
            }
        }
        if (cgmButton != null) {
            if (xDripIsBgSource && SP.getBoolean(R.string.key_show_cgm_button, false)) {
                cgmButton.setVisibility(View.VISIBLE);
            } else if (dexcomIsSource && SP.getBoolean(R.string.key_show_cgm_button, false)) {
                cgmButton.setVisibility(View.VISIBLE);
            } else {
                cgmButton.setVisibility(View.GONE);
            }
        }

        final ExtendedBolus extendedBolus = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(System.currentTimeMillis());
        String extendedBolusText = "";
        if (extendedBolusView != null) { // must not exists in all layouts
            if (shorttextmode) {
                if (extendedBolus != null && !pump.isFakingTempsByExtendedBoluses()) {
                    extendedBolusText = DecimalFormatter.to2Decimal(extendedBolus.absoluteRate()) + "U/h";
                }
            } else {
                if (extendedBolus != null && !pump.isFakingTempsByExtendedBoluses()) {
                    extendedBolusText = extendedBolus.toStringMedium();
                }
            }
            extendedBolusView.setText(extendedBolusText);
            extendedBolusView.setOnClickListener(v -> OKDialog.show(getActivity(), MainApp.gs(R.string.extended_bolus), extendedBolus.toString()));
            if (extendedBolusText.equals("")) {
                extendedBolusLayout.setVisibility(View.GONE);
                if (extendedBolusLayout != null) extendedBolusView.setVisibility(Config.NSCLIENT ? View.INVISIBLE : View.GONE);
            } else {
                extendedBolusView.setVisibility(View.VISIBLE);
                if (extendedBolusLayout != null) extendedBolusLayout.setVisibility(View.VISIBLE);
            }
        }


        // QuickWizard button
        QuickWizardEntry quickWizardEntry = QuickWizard.INSTANCE.getActive();
        if (quickWizardEntry != null && lastBG != null && pump.isInitialized() && !pump.isSuspended()) {
            quickWizardButton.setVisibility(View.VISIBLE);
            String text = quickWizardEntry.buttonText() + "\n" + DecimalFormatter.to0Decimal(quickWizardEntry.carbs()) + "g";
            BolusWizard wizard = quickWizardEntry.doCalc(profile, profileName, lastBG, false);
            text += " " + DecimalFormatter.toPumpSupportedBolus(wizard.getCalculatedTotalInsulin()) + "U";
            quickWizardButton.setText(text);
            if (wizard.getCalculatedTotalInsulin() <= 0)
                quickWizardButton.setVisibility(View.GONE);
        } else
            quickWizardButton.setVisibility(View.GONE);

        // **** Various treatment buttons ****
        if (carbsButton != null) {
            if (SP.getBoolean(R.string.key_show_carbs_button, true)
                    && (!ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription().storesCarbInfo ||
                    (pump.isInitialized() && !pump.isSuspended()))) {
                carbsButton.setVisibility(View.VISIBLE);
            } else {
                carbsButton.setVisibility(View.GONE);
            }
        }

        if (pump.isInitialized() && !pump.isSuspended()) {
            if (treatmentButton != null) {
                if (SP.getBoolean(R.string.key_show_treatment_button, false)) {
                    treatmentButton.setVisibility(View.VISIBLE);
                } else {
                    treatmentButton.setVisibility(View.GONE);
                }
            }
            if (pump.isInitialized() && !pump.isSuspended() && wizardButton != null) {
                if (SP.getBoolean(R.string.key_show_wizard_button, true)) {
                    wizardButton.setVisibility(View.VISIBLE);
                } else {
                    wizardButton.setVisibility(View.GONE);
                }
            }
            if (pump.isInitialized() && !pump.isSuspended() && insulinButton != null) {
                if (SP.getBoolean(R.string.key_show_insulin_button, true)) {
                    insulinButton.setVisibility(View.VISIBLE);
                } else {
                    insulinButton.setVisibility(View.GONE);
                }
            }
        }
        //Start with updating the BG as it is unaffected by loop.
        // **** BG value ****
        if (lastBG != null) {
            int color = MainApp.gc(R.color.inrange);
            if (lastBG.valueToUnits(units) < lowLine)
                color = MainApp.gc(R.color.low);
            else if (lastBG.valueToUnits(units) > highLine)
                color = MainApp.gc(R.color.high);
            bgView.setText(lastBG.valueToUnitsToString(units));
            arrowView.setText(lastBG.directionToSymbol());
            bgView.setTextColor(color);
            arrowView.setTextColor(color);
        }

        Integer flag = bgView.getPaintFlags();
        if (actualBG == null) {
            flag |= Paint.STRIKE_THRU_TEXT_FLAG;
        } else
            flag &= ~Paint.STRIKE_THRU_TEXT_FLAG;
        bgView.setPaintFlags(flag);

        //timeago

        if (timeAgoView != null) {
            timeAgoView.setText(DateUtil.minAgo(lastBG.date) + "′");
        }
        if (timeAgoShortView != null){
            timeAgoShortView.setText( DateUtil.minAgoShort(lastBG.date) + "′");
        }
//        if ((timeAgoView != null) && (lastBG.date <+4))   {
//            timeAgoView.setText(DateUtil.minAgo(lastBG.date) + "′");
//            Drawable drawable = timeAgoView.getBackground();
//            drawable.setColorFilter(new PorterDuffColorFilter(0xff666666, PorterDuff.Mode.SRC_ATOP));
//            Drawable wrapDrawable = DrawableCompat.wrap(ic_clock_28.getDrawable());
//            DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.rose));
//            DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
//            timeAgoView.setTextColor(MainApp.gc(R.color.peach));
//        } else  if ((timeAgoView != null) && (lastBG.date >+4)) {
//            timeAgoView.setText(DateUtil.minAgo(lastBG.date) + "′");
//            Drawable drawable = timeAgoView.getBackground();
//            drawable.setColorFilter(new PorterDuffColorFilter(0x00000000, PorterDuff.Mode.SRC_ATOP));
//            Drawable wrapDrawable = DrawableCompat.wrap(ic_clock_28.getDrawable());
//            DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.rose));
//            DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
//            timeAgoView.setTextColor(MainApp.gc(R.color.peach));
//        }

//        if ((timeAgoShortView != null) && (lastBG.date <+4))   {
//            timeAgoShortView.setText(DateUtil.minAgo(lastBG.date) + "′");
//            Drawable drawable = timeAgoShortView.getBackground();
//            drawable.setColorFilter(new PorterDuffColorFilter(0xff666666, PorterDuff.Mode.SRC_ATOP));
//            Drawable wrapDrawable = DrawableCompat.wrap(ic_clock_28.getDrawable());
//            DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.rose));
//            DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
//            timeAgoShortView.setTextColor(MainApp.gc(R.color.peach));
//        } else  if ((timeAgoShortView != null) && (lastBG.date >+4)) {
//            timeAgoShortView.setText(DateUtil.minAgo(lastBG.date) + "′");
//            Drawable drawable = timeAgoShortView.getBackground();
//            drawable.setColorFilter(new PorterDuffColorFilter(0x00000000, PorterDuff.Mode.SRC_ATOP));
//            Drawable wrapDrawable = DrawableCompat.wrap(ic_clock_28.getDrawable());
//            DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.rose));
//            DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
//            timeAgoShortView.setTextColor(MainApp.gc(R.color.peach));
//        }


        if (lastBG != null) {
            bgView.setTextColor(MainApp.gc(R.color.black));
//            int color = MainApp.gc(R.color.inrange);
            if (lastBG.valueToUnits(units) > lowLine )
//                color = MainApp.gc(R.color.low);
            {
                Drawable drawable = bgView.getBackground();
                drawable.setColorFilter(new PorterDuffColorFilter(0xFF2495d7, PorterDuff.Mode.MULTIPLY));
                bgView.setTextColor(MainApp.gc(R.color.white));
            }

            if (lastBG.valueToUnits(units) < lowLine)
//                color = MainApp.gc(R.color.low);
            {
                Drawable drawable = bgView.getBackground();
                drawable.setColorFilter(new PorterDuffColorFilter(0xfff0a30a, PorterDuff.Mode.SRC_ATOP));
                bgView.setTextColor(MainApp.gc(R.color.white));
            }

            else if (lastBG.valueToUnits(units) > highLine)
//                color = MainApp.gc(R.color.high);
            {
                Drawable drawable = bgView.getBackground();
                drawable.setColorFilter(new PorterDuffColorFilter(0xfff0a30a, PorterDuff.Mode.SRC_ATOP));
                bgView.setTextColor(MainApp.gc(R.color.white));
            }
            bgView.setText(lastBG.valueToUnitsToString(units));
            arrowView.setText(lastBG.directionToSymbol());
            arrowView.setTextColor(MainApp.gc(R.color.black));

            GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();
            if (glucoseStatus != null) {
                if (deltaView != null)
                    deltaView.setText(Profile.toUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units) + " " );
                if (deltaShortView != null)
                    deltaShortView.setText(Profile.toSignedUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units));
                if (avgdeltaView != null)
                    avgdeltaView.setText("øΔ15m: " + Profile.toUnitsString(glucoseStatus.short_avgdelta, glucoseStatus.short_avgdelta * Constants.MGDL_TO_MMOLL, units) +
                            "  øΔ40m: " + Profile.toUnitsString(glucoseStatus.long_avgdelta, glucoseStatus.long_avgdelta * Constants.MGDL_TO_MMOLL, units));
            } else {
                if (deltaView != null)
                    deltaView.setText("Δ " + MainApp.gs(R.string.notavailable));
                if (deltaShortView != null)
                    deltaShortView.setText("---");
                if (avgdeltaView != null)
                    avgdeltaView.setText("");
            }

            if (glucoseStatus != null && glucoseStatus.delta >+9) {
                Drawable drawable = deltaShortView.getBackground();
                drawable.setColorFilter(new PorterDuffColorFilter(0x90faae00, PorterDuff.Mode.SRC_ATOP));
                Drawable wrapDrawable = DrawableCompat.wrap(ic_delta_28.getDrawable());
                DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.concinnity_amber));
                DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
                deltaShortView.setTextColor(MainApp.gc(R.color.white));
            }
            else if (glucoseStatus != null && glucoseStatus.delta <-9) {
                Drawable drawable = deltaShortView.getBackground();
                drawable.setColorFilter(new PorterDuffColorFilter(0x90faae00, PorterDuff.Mode.SRC_ATOP));
                Drawable wrapDrawable = DrawableCompat.wrap(ic_delta_28.getDrawable());
                DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.concinnity_amber));
                DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
                deltaShortView.setTextColor(MainApp.gc(R.color.white));
            }
            else   {
                Drawable drawable = deltaShortView.getBackground();
                drawable.setColorFilter(new PorterDuffColorFilter(0xff999999, PorterDuff.Mode.SRC_IN));
                Drawable wrapDrawable = DrawableCompat.wrap(ic_delta_28.getDrawable());
                DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.concinnity_grey));
                DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_IN);
                deltaShortView.setTextColor(MainApp.gc(R.color.white));
            }
        }

        // iob
        TreatmentsPlugin.getPlugin().updateTotalIOBTreatments();
        TreatmentsPlugin.getPlugin().updateTotalIOBTempBasals();
        final IobTotal bolusIob = TreatmentsPlugin.getPlugin().getLastCalculationTreatments().round();
        final IobTotal basalIob = TreatmentsPlugin.getPlugin().getLastCalculationTempBasals().round();

        if (shorttextmode) {
            String iobtext = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob);
            iobView.setText(iobtext);
            iobView.setOnClickListener(v -> {
                String iobtext1 = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U\n"
                        + MainApp.gs(R.string.bolus) + ": " + DecimalFormatter.to2Decimal(bolusIob.iob) + "U\n"
                        + MainApp.gs(R.string.basal) + ": " + DecimalFormatter.to2Decimal(basalIob.basaliob) + "U\n";
                OKDialog.show(getActivity(), MainApp.gs(R.string.iob), iobtext1);
            });
        } else if (MainApp.sResources.getBoolean(R.bool.isTablet)) {
            String iobtext = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U ("
                    + MainApp.gs(R.string.bolus) + ": " + DecimalFormatter.to2Decimal(bolusIob.iob) + "U "
                    + MainApp.gs(R.string.basal) + ": " + DecimalFormatter.to2Decimal(basalIob.basaliob) + "U)";
            iobView.setText(iobtext);
        } else {
            String iobtext = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U ("
                    + DecimalFormatter.to2Decimal(bolusIob.iob) + "/"
                    + DecimalFormatter.to2Decimal(basalIob.basaliob) + ")";
            iobView.setText(iobtext);
        }
        if (iobView != null) {
            Drawable drawable = iobView.getBackground();
            drawable.setColorFilter(new PorterDuffColorFilter(0x90faae00, PorterDuff.Mode.SRC_ATOP));
            Drawable wrapDrawable = DrawableCompat.wrap(ic_pumpe_28.getDrawable());
            DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.concinnity_amber));
            DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
            iobView.setTextColor(MainApp.gc(R.color.white));
//            iobView.setTypeface(Typeface.DEFAULT_BOLD, Typeface.BOLD);

        } if ((bolusIob.iob + basalIob.basaliob) <= 0.00){
            Drawable drawable = iobView.getBackground();
            drawable.setColorFilter(new PorterDuffColorFilter(0xff999999, PorterDuff.Mode.SRC_IN));
            Drawable wrapDrawable = DrawableCompat.wrap(ic_pumpe_28.getDrawable());
            DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.concinnity_grey));
            DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_IN);
            iobView.setTextColor(MainApp.gc(R.color.white));
        }

        // cob
        if (cobView != null) { // view must not exists
            String cobText = MainApp.gs(R.string.value_unavailable_short);
            CobInfo cobInfo = IobCobCalculatorPlugin.getPlugin().getCobInfo(false, "Overview COB");
            if (cobInfo.displayCob != null) {
                cobText = DecimalFormatter.to0Decimal(cobInfo.displayCob);
                Drawable drawable = cobView.getBackground();
                drawable.setColorFilter(new PorterDuffColorFilter(0x90faae00, PorterDuff.Mode.SRC_ATOP));
                Drawable wrapDrawable = DrawableCompat.wrap(ic_carb_28.getDrawable());
                DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.concinnity_amber));
                DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
                cobView.setTextColor(MainApp.gc(R.color.white));

                if (cobInfo.futureCarbs > 0)
                    cobText += "/" + DecimalFormatter.to0Decimal(cobInfo.futureCarbs) ;

            }
            if (cobInfo.displayCob != null && cobInfo.displayCob == 0) {
                cobText = DecimalFormatter.to0Decimal(cobInfo.displayCob);
                Drawable drawable = cobView.getBackground();
                drawable.setColorFilter(new PorterDuffColorFilter(0xff999999, PorterDuff.Mode.SRC_IN));
                Drawable wrapDrawable = DrawableCompat.wrap(ic_carb_28.getDrawable());
                DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.concinnity_grey));
                DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_IN);
                cobView.setTextColor(MainApp.gc(R.color.white));
            }
            cobView.setText(cobText);
        }
        // bas
        final TemporaryBasal activeTemp = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(System.currentTimeMillis());
        String basalText = "";
        if (shorttextmode) {
            if (activeTemp != null) {
//               basalText = "T: " + activeTemp.toStringVeryShort();
                basalText = activeTemp.toStringVeryShort();
            } else {
                basalText = MainApp.gs(R.string.pump_basebasalrate,profile.getBasal());
            }
            baseBasalView.setOnClickListener(v -> {
                String fullText = MainApp.gs(R.string.pump_basebasalrate_label) + ": " + MainApp.gs(R.string.pump_basebasalrate,profile.getBasal()) + "\n";
                if (activeTemp != null) {
                    fullText += MainApp.gs(R.string.pump_tempbasal_label) + ": " + activeTemp.toStringFull();
                }
                OKDialog.show(getActivity(), MainApp.gs(R.string.basal), fullText);
            });

        } else {
            if (activeTemp != null) {
                basalText = activeTemp.toStringFull();
            } else {
                basalText = MainApp.gs(R.string.pump_basebasalrate,profile.getBasal());
            }
        }

/*        baseBasalView.setText(basalText);
        if (activeTemp != null) {
            baseBasalView.setTextColor(MainApp.gc(R.color.gray));
            baseBasalView.setTypeface(Typeface.DEFAULT_BOLD, Typeface.BOLD);
        } else {
            baseBasalView.setTextColor(MainApp.gc(R.color.white));
        }*/

        baseBasalView.setText(basalText);

        if (activeTemp != null) {
            Drawable drawable = baseBasalView.getBackground();
            drawable.setColorFilter(new PorterDuffColorFilter(0x90faae00, PorterDuff.Mode.SRC_ATOP));
            Drawable wrapDrawable = DrawableCompat.wrap(ic_bas_28.getDrawable());
            DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.concinnity_amber));
            DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
            baseBasalView.setTextColor(MainApp.gc(R.color.white));
        } else {
            Drawable drawable = baseBasalView.getBackground();
            drawable.setColorFilter(new PorterDuffColorFilter(0xff999999, PorterDuff.Mode.SRC_IN));
            Drawable wrapDrawable = DrawableCompat.wrap(ic_bas_28.getDrawable());
            DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.concinnity_grey));
            DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_IN);
            baseBasalView.setTextColor(MainApp.gc(R.color.white));
        }

        if (statuslightsLayout != null)
            if (SP.getBoolean(R.string.key_show_statuslights, false)) {
                StatuslightHandler handler = new StatuslightHandler();
                if (SP.getBoolean(R.string.key_show_statuslights_extended, false)) {
                    handler.extendedStatuslight(cageView, iageView, reservoirView, sageView, batteryView);
                    statuslightsLayout.setVisibility(View.VISIBLE);
                } else {
                    handler.statuslight(cageView, iageView, reservoirView, sageView, batteryView);
                    statuslightsLayout.setVisibility(View.VISIBLE);
                }
            } else {
                statuslightsLayout.setVisibility(View.GONE);
            }

        boolean predictionsAvailable;
        if (Config.APS)
            predictionsAvailable = finalLastRun != null && finalLastRun.request.hasPredictions;
        else if (Config.NSCLIENT)
            predictionsAvailable = true;
        else
            predictionsAvailable = false;
        final boolean finalPredictionsAvailable = predictionsAvailable;

        // pump status from ns
        if (pumpDeviceStatusView != null) {
            pumpDeviceStatusView.setText(NSDeviceStatus.getInstance().getPumpStatus());
            pumpDeviceStatusView.setOnClickListener(v -> OKDialog.show(getActivity(), MainApp.gs(R.string.pump), NSDeviceStatus.getInstance().getExtendedPumpStatus()));
        }

        // OpenAPS status from ns
        if (openapsDeviceStatusView != null) {
            openapsDeviceStatusView.setText(NSDeviceStatus.getInstance().getOpenApsStatus());
            openapsDeviceStatusView.setOnClickListener(v -> OKDialog.show(getActivity(), MainApp.gs(R.string.openaps), NSDeviceStatus.getInstance().getExtendedOpenApsStatus()));
        }

        // Uploader status from ns
        if (uploaderDeviceStatusView != null) {
            uploaderDeviceStatusView.setText(NSDeviceStatus.getInstance().getUploaderStatusSpanned());
            uploaderDeviceStatusView.setOnClickListener(v -> OKDialog.show(getActivity(), MainApp.gs(R.string.uploader), NSDeviceStatus.getInstance().getExtendedUploaderStatus()));
        }

        // Sensitivity
        //       if (sensitivityView != null) {
        //           AutosensData autosensData = IobCobCalculatorPlugin.getPlugin().getLastAutosensData("Overview");
        //           if (autosensData != null)
        //               sensitivityView.setText(String.format(Locale.ENGLISH, "%.0f%%", autosensData.autosensResult.ratio * 100));
        //           else
        //               sensitivityView.setText("");
        //       }

        // Sensitivity
        AutosensData autosensData;
        autosensData = IobCobCalculatorPlugin.getPlugin().getLastAutosensData( "Overview" );
        sensitivityView.setText( String.format( Locale.ENGLISH, "%.0f", autosensData.autosensResult.ratio * 100 ) );

        if ((sensitivityView != null) && autosensData.autosensResult.ratio > 1.09) {
            Drawable drawable = sensitivityView.getBackground();
            drawable.setColorFilter( new PorterDuffColorFilter( 0x90faae00, PorterDuff.Mode.SRC_ATOP ) );
            Drawable wrapDrawable = DrawableCompat.wrap(ic_as_28.getDrawable());
            DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.concinnity_amber));
            DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
            sensitivityView.setTextColor( MainApp.gc( R.color.white ) );
        }
        else if ((sensitivityView != null) && autosensData.autosensResult.ratio < 0.90) {
            Drawable drawable = sensitivityView.getBackground();
            drawable.setColorFilter( new PorterDuffColorFilter( 0x90faae00, PorterDuff.Mode.SRC_ATOP ) );
            Drawable wrapDrawable = DrawableCompat.wrap(ic_as_28.getDrawable());
            DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.concinnity_amber));
            DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_ATOP);
            sensitivityView.setTextColor( MainApp.gc( R.color.white ) );
        }
        else if (sensitivityView != null){
            Drawable drawable = sensitivityView.getBackground();
            drawable.setColorFilter( new PorterDuffColorFilter( 0xff999999, PorterDuff.Mode.SRC_IN ) );
            Drawable wrapDrawable = DrawableCompat.wrap(ic_as_28.getDrawable());
            DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(getContext(), R.color.concinnity_grey));
            DrawableCompat.setTintMode(wrapDrawable, PorterDuff.Mode.SRC_IN);
            sensitivityView.setTextColor( MainApp.gc( R.color.white ) );
        }

        // ****** GRAPH *******

        new Thread(() -> {
            // allign to hours
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.add(Calendar.HOUR, 1);

            int hoursToFetch;
            final long toTime;
            final long fromTime;
            final long endTime;

            APSResult apsResult = null;

            if (finalPredictionsAvailable && SP.getBoolean("showprediction", false)) {
                if (Config.APS)
                    apsResult = finalLastRun.constraintsProcessed;
                else
                    apsResult = NSDeviceStatus.getAPSResult();
                int predHours = (int) (Math.ceil(apsResult.getLatestPredictionsTime() - System.currentTimeMillis()) / (60 * 60 * 1000));
                predHours = Math.min(2, predHours);
                predHours = Math.max(0, predHours);
                hoursToFetch = rangeToDisplay - predHours;
                toTime = calendar.getTimeInMillis() + 100000; // little bit more to avoid wrong rounding - Graphview specific
                fromTime = toTime - T.hours(hoursToFetch).msecs();
                endTime = toTime + T.hours(predHours).msecs();
            } else {
                hoursToFetch = rangeToDisplay;
                toTime = calendar.getTimeInMillis() + 100000; // little bit more to avoid wrong rounding - Graphview specific
                fromTime = toTime - T.hours(hoursToFetch).msecs();
                endTime = toTime;
            }


            final long now = System.currentTimeMillis();

            //  ------------------ 1st graph
            if (L.isEnabled(L.OVERVIEW))
                Profiler.log(log, from + " - 1st graph - START", updateGUIStart);

            final GraphData graphData = new GraphData(bgGraph, IobCobCalculatorPlugin.getPlugin());

            // **** In range Area ****
            graphData.addInRangeArea(fromTime, endTime, lowLine, highLine);

            // **** BG ****
            if (finalPredictionsAvailable && SP.getBoolean("showprediction", false))
                graphData.addBgReadings(fromTime, toTime, lowLine, highLine,
                        apsResult.getPredictions());
            else
                graphData.addBgReadings(fromTime, toTime, lowLine, highLine, null);

            // set manual x bounds to have nice steps
            graphData.formatAxis(fromTime, endTime);

            // Treatments
            if (SP.getBoolean("showtreatments", true)) {
                graphData.addTreatments(fromTime, endTime);
            }

            if (SP.getBoolean("showactivityprimary", true)) {
                graphData.addActivity(fromTime, endTime, false, 0.8d);
            }

            // add basal data
            if (pump.getPumpDescription().isTempBasalCapable && SP.getBoolean("showbasals", true)) {
                graphData.addBasals(fromTime, now, lowLine / graphData.maxY / 1.2d);
            }

            // add target line
            graphData.addTargetLine(fromTime, toTime, profile);

            // **** NOW line ****
            graphData.addNowLine(now);

            // ------------------ 2nd graph
            if (L.isEnabled(L.OVERVIEW))
                Profiler.log(log, from + " - other graphs - START", updateGUIStart);

            final GraphData iobGraphData = new GraphData(iobGraph, IobCobCalculatorPlugin.getPlugin());
            final GraphData cobGraphData = new GraphData(cobGraph, IobCobCalculatorPlugin.getPlugin());
            final GraphData devGraphData = new GraphData(devGraph, IobCobCalculatorPlugin.getPlugin());

            boolean useIobForScale = true;
            boolean useCobForScale = true;

            boolean useDevForScale = false;
            boolean useRatioForScale = false;
            boolean useDSForScale = false;
            boolean useIAForScale = false;

            boolean anyDev = false;
            boolean anyIob = false;
            boolean anyCob = false;

            if (SP.getBoolean("showdeviations", false)) {
                useDevForScale = true;
                anyDev = true;
            } else if (SP.getBoolean("showratios", false)) {
                useRatioForScale = true;
                anyDev = true;
            } else if (SP.getBoolean("showactivitysecondary", false)) {
                useIAForScale = true;
                anyDev = true;
            } else if (SP.getBoolean("showdevslope", false)) {
                useDSForScale = true;
                anyDev = true;
            }

            if (SP.getBoolean("showiob", false)) {
                useIobForScale = true;
                anyIob = true;
            }

            if (SP.getBoolean("showcob", false)) {
                useCobForScale = true;
                anyCob = true;
            }

            final boolean showDevGraph = anyDev;
            final boolean showiobGraph = anyIob;
            final boolean showcobGraph = anyCob;

            iobGraphData.addIob(fromTime, now, useIobForScale, useCobForScale ? 1d : 0.5d, SP.getBoolean("showprediction", false));
            cobGraphData.addCob(fromTime, now, useCobForScale, useCobForScale ? 1d : 0.5d);

            if (SP.getBoolean("showdeviations", false)){
                devGraphData.addDeviations(fromTime, now, useDevForScale, 1d);
            }

            if (SP.getBoolean("showratios", false))
                devGraphData.addRatio(fromTime, now, useRatioForScale, 1d);
            if (SP.getBoolean("showactivitysecondary", true))
                devGraphData.addActivity(fromTime, endTime, useIAForScale, 0.8d);
            if (SP.getBoolean("showdevslope", false) && MainApp.devBranch)
                devGraphData.addDeviationSlope(fromTime, now, useDSForScale, 1d);

            // dev graph
            // **** NOW line ****
            // set manual x bounds to have nice steps
            devGraphData.formatAxis(fromTime, endTime);
            devGraphData.addNowLine(now);

            // ------------------ COB graph

            cobGraphData.formatAxis(fromTime, endTime);
            cobGraphData.addNowLine(now);

            // ------------------ IOB graph

            iobGraphData.formatAxis(fromTime, endTime);
            iobGraphData.addNowLine(now);

            // do GUI update
            FragmentActivity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
//                    iobGraph.setVisibility(View.VISIBLE);
//                    cobGraph.setVisibility(View.VISIBLE);


                    if (showiobGraph) {
                        iobGraph.setVisibility(View.VISIBLE);
                    } else {
                        iobGraph.setVisibility(View.GONE);
                    }

                    if (showcobGraph) {
                        cobGraph.setVisibility(View.VISIBLE);
                    } else {
                        cobGraph.setVisibility(View.GONE);
                    }

                    if (showDevGraph) {
                        devGraph.setVisibility(View.VISIBLE);
                    } else {
                        devGraph.setVisibility(View.GONE);
                    }

                    // finally enforce drawing of graphs
                    graphData.performUpdate();
                    iobGraphData.performUpdate();
                    cobGraphData.performUpdate();
                    devGraphData.performUpdate();
                    if (L.isEnabled(L.OVERVIEW))
                        Profiler.log(log, from + " - onDataChanged", updateGUIStart);
                });
            }
        }).start();

        if (L.isEnabled(L.OVERVIEW))
            Profiler.log(log, from, updateGUIStart);
    }


}