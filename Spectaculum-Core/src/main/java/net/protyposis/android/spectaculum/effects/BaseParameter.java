/*
 * Copyright (c) 2014 Mario Guggenberger <mg@protyposis.net>
 *
 * This file is part of MediaPlayer-Extended.
 *
 * MediaPlayer-Extended is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MediaPlayer-Extended is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MediaPlayer-Extended.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.protyposis.android.spectaculum.effects;

/**
 * Created by maguggen on 21.08.2014.
 */
public abstract class BaseParameter<T> implements Parameter<T> {

    private String mName;
    private Type mType;
    private Delegate<T> mDelegate;
    private String mDescription;
    private Listener mListener;
    private ParameterHandler mHandler;

    protected BaseParameter(String name, Type type, Delegate<T> delegate) {
        mName = name;
        mType = type;
        mDelegate = delegate;
    }

    public BaseParameter(String name, Type type, Delegate<T> delegate, String description) {
        this(name, type, delegate);
        this.mDescription = description;
    }

    public Type getType() {
        return mType;
    }

    public String getName() {
        return mName;
    }

    protected Delegate<T> getDelegate() {
        return mDelegate;
    }

    public String getDescription() {
        return mDescription;
    }

    public abstract void reset();

    public void setListener(Listener listener) {
        mListener = listener;
    }

    protected void fireParameterChanged() {
        if(mListener != null) {
            mListener.onParameterChanged(this);
        }
    }

    protected void setDelegateValue(final T value) {
        if(mHandler != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mDelegate.setValue(value);
                }
            });
        } else {
            mDelegate.setValue(value);
        }
        fireParameterChanged();
    }

    /**
     * Sets a ParameterHandler on which parameter value changes will be executed. Parameter values
     * need to be set on the GL thread where the effect that the parameter belongs is active, and
     * this handler can be used to hand the parameter setting over to the GL thread.
     * If no handler is set, parameters will be set on the caller thread.
     * @param handler the parameter handler to set, or null to unset
     */
    public void setHandler(ParameterHandler handler) {
        mHandler = handler;
    }
}