package sk.codekitchen.smartfuel.ui.views;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.EditText;

/**
 * @author Gabriel Lehocky
 *
 * Extends EditText view by custom font type
 */
public class EditLightTextView extends EditText{

    public EditLightTextView(Context context, AttributeSet attrs){
        super(context, attrs);
        this.setTypeface(Typeface.createFromAsset(context.getAssets(), "Fonts/ProximaNova-Light.otf"));
    }
}