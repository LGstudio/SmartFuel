package sk.codekitchen.smartfuel.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import sk.codekitchen.smartfuel.R;
import sk.codekitchen.smartfuel.ui.views.Colors;
import sk.codekitchen.smartfuel.ui.views.LightTextView;
import sk.codekitchen.smartfuel.ui.views.SemiboldTextView;
import sk.codekitchen.smartfuel.ui.views.Utils;

/**
 * @author Gabriel Lehocky
 */
public class FragmentRecorder extends Fragment implements View.OnClickListener{

    /**
     * isOverLimit: set to true in case of overspeeding
     * WARNING: after changing this variable please call the changeColorBySpeed() method
     *      to change colors of the view
     */
    private boolean isOverLimit = false;

    /**
     * speedOrPercent: change between the 2 view modes
     * false = percent
     * true = speed
     */
    private boolean speedOrPercent = false;

    /**
     * speedLimit: 0 if unknown or unlimited
     */
    private int speedLimit = 0;

    private SemiboldTextView noGps;
    private SemiboldTextView noSignal;

    private ProgressBar progressBar;
    private LightTextView progressValue;
    private LightTextView progressSufix;
    private LightTextView progressComment;
    private SemiboldTextView progressCommentBold;
    private LinearLayout progressData;

    private int progressMax;
    private int progressPercent = 0;
    private int progressSpeed = 0;

    private LinearLayout btnSpeed;
    private LinearLayout btnPercent;
    private ImageView icoBtnPercent;
    private ImageView icoBtnSpeed;
    private LightTextView txtBtnPercent;
    private LightTextView txtBtnSpeed;

    private LinearLayout maxPermittedSign;
    private SemiboldTextView maxPermittedSpeed;

    private SemiboldTextView pointCurrent;
    private SemiboldTextView pointOverall;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recorder, container, false);

        // speed/percent switch
        btnSpeed = (LinearLayout) view.findViewById(R.id.btn_speed);
        btnPercent = (LinearLayout) view.findViewById(R.id.btn_percent);
        btnSpeed.setOnClickListener(this);
        btnPercent.setOnClickListener(this);
        icoBtnPercent = (ImageView) view.findViewById(R.id.icon_percent);
        icoBtnSpeed = (ImageView) view.findViewById(R.id.icon_speed);
        txtBtnPercent = (LightTextView) view.findViewById(R.id.txt_percent);
        txtBtnSpeed = (LightTextView) view.findViewById(R.id.txt_speed);

        // progressbar
        progressBar = (ProgressBar) view.findViewById(R.id.progressBar);
        progressValue = (LightTextView) view.findViewById(R.id.progress_value);
        progressSufix = (LightTextView) view.findViewById(R.id.progress_symbol);
        progressComment = (LightTextView) view.findViewById(R.id.progress_comment);
        progressCommentBold = (SemiboldTextView) view.findViewById(R.id.progress_comment_bold);
        progressData = (LinearLayout) view.findViewById(R.id.progress_central_data);

        noGps = (SemiboldTextView) view.findViewById(R.id.progress_no_gps);
        noSignal = (SemiboldTextView) view.findViewById(R.id.progress_no_signal);
        noGps.setOnClickListener(this);
        maxPermittedSpeed = (SemiboldTextView) view.findViewById(R.id.max_permitted_speed);
        maxPermittedSign =(LinearLayout) view.findViewById(R.id.max_permitted_sign);

        // points data
        pointCurrent = (SemiboldTextView) view.findViewById(R.id.actual_points);
        pointOverall = (SemiboldTextView) view.findViewById(R.id.overall_points);

        return view;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_speed:
                if (!speedOrPercent)
                    changeColorsBySwitch();
                break;
            case R.id.btn_percent:
                if(speedOrPercent)
                    changeColorsBySwitch();
                break;
            case R.id.progress_no_gps:
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                this.startActivity(intent);
                break;
        }
    }

    /**
     * Called when the view has to change to the other view.
     * (from percent to speed or from speed to percent view)
     */
    private void changeColorsBySwitch() {
        speedOrPercent = !speedOrPercent;

        // change icon alphas
        float a = icoBtnPercent.getAlpha();
        icoBtnPercent.setAlpha(icoBtnSpeed.getAlpha());
        icoBtnSpeed.setAlpha(a);

        // change text colors
        int c = txtBtnPercent.getCurrentTextColor();
        txtBtnPercent.setTextColor(txtBtnSpeed.getCurrentTextColor());
        txtBtnSpeed.setTextColor(c);

        changeColorBySpeed();

        if (speedOrPercent) { // speed
            progressSufix.setText("");
            progressComment.setText("");
            progressCommentBold.setText(getString(R.string.rec_kmph));

            setSpeedLimit(speedLimit);

            // ---- TEST DATA ONLY --------------------------------------------------
            changeProgress(progressSpeed);
            // -------------------------------------------------------------------
        }
        else { // percent
            progressSufix.setText(getString(R.string.rec_percent_symbol));
            progressComment.setText(getString(R.string.rec_comment_1));
            progressCommentBold.setText(getString(R.string.rec_comment_2));

            changeProgressMax(100); // 100% is always max for ths view

            // ---- TEST DATA ONLY --------------------------------------------------
            changeProgress(progressPercent);
            // -------------------------------------------------------------------
        }
    }

    /**
     * Changes color scheme based on the isOverLimit and
     */
    public void changeColorBySpeed() {
        if (speedOrPercent) { // speed
            Utils.setBackgroundOfView(getActivity(), btnPercent, R.drawable.round_transparent);
            if (isOverLimit) {
                Utils.setBackgroundOfView(getActivity(), btnSpeed, R.drawable.round_bad_box_right);
                Utils.setProgressBarProgress(getActivity(), progressBar, R.drawable.progressbar_arch_grad_bad);
                progressValue.setTextColor(Colors.RED);
            } else {
                Utils.setBackgroundOfView(getActivity(), btnSpeed, R.drawable.round_highlight_box_right);
                Utils.setProgressBarProgress(getActivity(), progressBar, R.drawable.progressbar_arch_grad_good);
                progressValue.setTextColor(Colors.WHITE);
            }
        } else { // percent
            Utils.setBackgroundOfView(getActivity(), btnSpeed, R.drawable.round_transparent);
            if (isOverLimit) {
                Utils.setBackgroundOfView(getActivity(), btnPercent, R.drawable.round_bad_box_left);
                Utils.setProgressBarProgress(getActivity(), progressBar, R.drawable.progressbar_arch_grad_bad);
                progressSufix.setTextColor(Colors.RED);
                progressValue.setTextColor(Colors.ORANGE);

            }
            else {
                Utils.setBackgroundOfView(getActivity(), btnPercent, R.drawable.round_highlight_box_left);
                Utils.setProgressBarProgress(getActivity(), progressBar, R.drawable.progressbar_arch_grad_good);
                progressSufix.setTextColor(Colors.MAIN);
                progressValue.setTextColor(Colors.WHITE);
            }
        }
    }

    /**
     * changes progressbar maximal value
     * by hacking it to make it an 270 degree arc instead of a full ring
     * @param max
     */
    private void changeProgressMax(int max) {
        progressMax = max;
        progressBar.setMax(progressMax + progressMax / 3);

        if (speedOrPercent) { // speed
            if (progressMax <= progressSpeed){
                isOverLimit = false;
                changeColorBySpeed();
            }
            else {
                progressBar.setProgress(progressMax);
                isOverLimit = true;
                changeColorBySpeed();
            }
        }
    }

    /**
     * changes the Actual value of the progressbar and changes the value text also
     * @param val
     */
    private void changeProgress(int val){
        if (val < progressMax){
            progressBar.setProgress(val);
        }
        else {
            progressBar.setProgress(progressMax);
        }
        progressValue.setText(String.valueOf(val));
    }

    /**
     * changes the speedlimit sign and also the progressbar max in case of speed
     * @param l - limit
     */
    public void setSpeedLimit(int l){
        if (l > 0) {
            maxPermittedSign.setVisibility(View.VISIBLE);
            maxPermittedSpeed.setText(String.valueOf(l));
            if (speedOrPercent) changeProgressMax(l);
        }
        else if (l == 0){
            maxPermittedSign.setVisibility(View.INVISIBLE);
        }
        speedLimit = l;
    }

    /**
     * changes layout based on gps provider
     * @param gps
     */
    public void isGPS(boolean gps){
        if (gps) {
            noGps.setVisibility(View.GONE);
            progressData.setVisibility(View.VISIBLE);
        }
        else {
            noGps.setVisibility(View.VISIBLE);
            progressData.setVisibility(View.GONE);
        }
    }

    /**
     * changes layout when no signal or signal is back
     * @param signal
     */
    public void isSignal(boolean signal){
        if (signal) {
            noSignal.setVisibility(View.GONE);
            progressData.setVisibility(View.VISIBLE);
        }
        else {
            noSignal.setVisibility(View.VISIBLE);
            progressData.setVisibility(View.GONE);
        }
    }

    /**
     * changes layout based on current speed
     * @param s
     */
    public void setSpeed(int s){
        progressSpeed = s;
        if (speedOrPercent) changeProgress(s);
        if (progressSpeed > speedLimit) {
            isOverLimit = true;
            changeColorBySpeed();
        }
    }

    /**
     * changes layout based on vurrent percents
     * @param p
     */
    public void setPercent(int p){
        progressPercent = p;
        if (!speedOrPercent) changeProgress(p);
    }

    /**
     * sets current points text
     * @param val
     */
    public void setCurrentPoints(int val){
        pointCurrent.setText(String.valueOf(val));
    }

    /**
     * sets overall points text
     * @param val
     */
    public void setOverallPoints(int val){
        pointOverall.setText(String.valueOf(val));
    }

}