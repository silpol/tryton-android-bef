/*
    Tryton Android
    Copyright (C) 2012 SARL SCOP Scil (contact@scil.coop)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.tryton.client;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.ProgressBar;

import org.tryton.client.data.Session;
import org.tryton.client.models.Model;
import org.tryton.client.tools.AlertBuilder;
import org.tryton.client.tools.DelayedRequester;
import org.tryton.client.tools.TrytonCall;

/** List pending requests and handles sending them all.
 * It reads data from DelayedRequester.current.  */
public class PendingRequests extends Activity implements Handler.Callback {

    private TextView remaining;
    private ProgressBar progressBar;
    private int callId;
    private int initialCallCount;
    private int progress;
    private int currentTempId;
    private boolean kill;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            this.callId = state.getInt("callId");
            this.initialCallCount = state.getInt("initialCallCount");
            this.progress = state.getInt("progress");
            this.currentTempId = state.getInt("currentTempId");
            if (this.callId != 0) {
                TrytonCall.update(this.callId, new Handler(this));
            }
        } else {
            this.initialCallCount = DelayedRequester.current.getQueueSize();
        }
        this.setContentView(R.layout.pending_requests);
        this.remaining = (TextView) this.findViewById(R.id.pending_remaining);
        this.progressBar = (ProgressBar) this.findViewById(R.id.pending_progress);
        this.progressBar.setMax(this.initialCallCount);
        this.update();
        if (this.callId == 0) {
            this.sendNextCommand();
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("callId", this.callId);
        outState.putInt("initialCallCount", this.initialCallCount);
        outState.putInt("progress", this.progress);
        outState.putInt("currentTempId", this.currentTempId);
    }

    public void onDestroy() {
        super.onDestroy();
        if (this.kill) {
            DelayedRequester.current.updateNotification(this);
        }
    }

    private void next() {
        this.progress++;
        DelayedRequester.current.commandDone(this);
        this.update();
        if (!this.sendNextCommand()) {
            this.kill = true;
            this.finish();
        }
    }

    private boolean sendNextCommand() {
        DelayedRequester req = DelayedRequester.current;
        if (req.getQueueSize() > 0) {
            DelayedRequester.Command cmd = req.getNextCommand();
            Model data = cmd.getData();
            Session s = Session.current;
            switch (cmd.getCmd()) {
            case DelayedRequester.CMD_CREATE:
                // Remove the negative id before sending
                this.currentTempId = (Integer) data.get("id");
                data.set("id", null);
                this.callId = TrytonCall.saveData(s.userId, s.cookie,
                                                  s.prefs, data,
                                                  null, this,
                                                  new Handler(this));
                break;
            case DelayedRequester.CMD_UPDATE:
                this.currentTempId = 0;
                this.callId = TrytonCall.saveData(s.userId, s.cookie,
                                                  s.prefs, data,
                                                  null, this,
                                                  new Handler(this));
                break;
            case DelayedRequester.CMD_DELETE:
                this.currentTempId = 0;
                int id = (Integer) data.get("id");
                String className = data.getClassName();
                this.callId = TrytonCall.deleteData(s.userId, s.cookie,
                                                    s.prefs, id, className,
                                                    new Handler(this));
                break;
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            this.kill = true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void update() {
        this.progressBar.setProgress(this.progress);
        int count = DelayedRequester.current.getQueueSize();
        this.remaining.setText(String.format(this.getString(R.string.requester_pending),
                                             count));
    }
    
    /** Handle TrytonCall feedback. */
    @SuppressWarnings("unchecked")
    public boolean handleMessage(Message msg) {
        // Process message
        switch (msg.what) {
        case TrytonCall.CALL_SAVE_OK:
            this.callId = 0;
            this.next();
            break;
        case TrytonCall.CALL_DELETE_OK:
            this.callId = 0;
            this.next();
            break;
        case TrytonCall.CALL_SAVE_NOK:
        case TrytonCall.CALL_DELETE_NOK:
            Exception e = (Exception) msg.obj;
            if (!AlertBuilder.showUserError(e, this)
                && !AlertBuilder.showUserError(e, this)) {
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setTitle(R.string.error);
                b.setMessage(R.string.network_error);
                b.show();
                ((Exception)msg.obj).printStackTrace();
            }
            break;
        }
        return true;
    }
}