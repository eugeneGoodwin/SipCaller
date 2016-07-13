package com.test.caller;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import java.lang.reflect.Method;

/**
 * EditText which suppresses IME show up.
 */
public class DigitsEditText extends EditText {
    public DigitsEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        setInputType(getInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setShowSoftInputOnFocus(false);
        }else{
            try {
                final Method method = EditText.class.getMethod(
                        "setShowSoftInputOnFocus"
                        , new Class[]{boolean.class});
                method.setAccessible(true);
                method.invoke(this, false);
            } catch (Exception e) {
            }
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        final InputMethodManager imm = ((InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE));
        if (imm != null && imm.isActive(this)) {
            imm.hideSoftInputFromWindow(getApplicationWindowToken(), 0);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final boolean ret = super.onTouchEvent(event);
        // Must be done after super.onTouchEvent()
        final InputMethodManager imm = ((InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE));
        if (imm != null && imm.isActive(this)) {
            imm.hideSoftInputFromWindow(getApplicationWindowToken(), 0);
        }
        return ret;
    }
}
