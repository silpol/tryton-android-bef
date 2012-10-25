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
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.tryton.client.data.Session;
import org.tryton.client.models.Preferences;
import org.tryton.client.tools.TrytonCall;

/** Start activity. Shows login form. */
public class Start extends Activity implements Handler.Callback {

    /** Boolean to wake up login or make it inactive when user is logged in.
     * The activity goes down when user logs in and is woken up when the
     * user logs out.
     */
    private static boolean awake = true;

    public static boolean isAwake() {
        return awake;
    }

    private String serverVersion;

    private TextView versionLabel;
    private EditText login;
    private EditText password;
    private Button loginBtn;
    private ProgressDialog loadingDialog;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            // Recreated from a saved state
            this.serverVersion = state.getString("version");
        }
        // Load configuration for TrytonCall
        TrytonCall.setup(Configure.getHost(this), Configure.getDatabase(this));
        // Load views from xml resource
        setContentView(R.layout.main);
        this.versionLabel = (TextView) this.findViewById(R.id.server_version);
        this.login = (EditText) this.findViewById(R.id.login);
        this.password = (EditText) this.findViewById(R.id.password);
        this.loginBtn = (Button) this.findViewById(R.id.login_btn);
        // Init is done, check if user is already logged in to skip login
        if (Session.current.userId != -1) {
            Intent i = new Intent(this, org.tryton.client.Menu.class);
            this.startActivity(i);
            awake = false; // zzzz
            return;
        }
        // Update server version label
        this.updateVersionLabel();
    }

    /** Save current state before killing, if necessary (called by system) */
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(this.serverVersion, "version");
    }

    /** Called when activity comes to front */
    @Override
    public void onResume() {
        super.onResume();
        if (!isAwake()) {
            // zzz (quit)
            this.finish();
            return;
        }
        // When returning from configuration TrytonCall host may have changed
        TrytonCall.serverVersion(new Handler(this));
    }

    /** Update display according to stored version */
    public void updateVersionLabel() {
        if (this.serverVersion == null) {
            // Unknown version, server is unavailable
            this.versionLabel.setText(R.string.login_server_unavailable);
            this.login.setEnabled(false);
            this.password.setEnabled(false);
            this.loginBtn.setEnabled(false);
        } else {
            this.versionLabel.setText(this.serverVersion);
            this.login.setEnabled(true);
            this.password.setEnabled(true);
            this.loginBtn.setEnabled(true);
        }
    }

    /** Handle TrytonCall feedback. */
    public boolean handleMessage(Message msg) {
        // Close the loading dialog if present
        this.hideLoadingDialog();
        // Process message
        switch (msg.what) {
        case TrytonCall.CALL_VERSION_OK:
            this.serverVersion = (String) msg.obj;
            this.updateVersionLabel();
            break;
        case TrytonCall.CALL_VERSION_NOK:
            this.serverVersion = null;
            this.updateVersionLabel();
            if (msg.obj instanceof Exception) {
                ((Exception)msg.obj).printStackTrace();
            }
            break;
        case TrytonCall.CALL_LOGIN_OK:
            if (msg.arg1 != 0) {
                // Login successfull. Save data in session
                Object[] resp = (Object[]) msg.obj;
                int userId = (Integer) resp[0];
                String cookie = (String) resp[1];
                Session.current.user = this.login.getText().toString();
                Session.current.password = this.login.getText().toString();
                Session.current.userId = userId;
                Session.current.cookie = cookie;
                // Get user preferences
                TrytonCall.getPreferences(userId, cookie, new Handler(this));
            } else {
                // Show login error
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setMessage(R.string.login_bad_login);
                b.setPositiveButton(android.R.string.ok, null);
                b.show();
            }
            break;
        case TrytonCall.CALL_PREFERENCES_OK:
            // Save the preferences
            Session.current.prefs = (Preferences) msg.obj;
            // Go to menu
            Intent i = new Intent(this, org.tryton.client.Menu.class);
            this.startActivity(i);
            // Falling asleep...
            awake = false;
            break;
        case TrytonCall.CALL_LOGIN_NOK:
        case TrytonCall.CALL_PREFERENCES_NOK:
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle(R.string.error);
            b.setMessage(R.string.network_error);
            b.show();
            break;
        }
        return true;
    }

    // Mapped by xml on login button click
    public void login(View v) {
        // Show loading dialog
        this.showLoadingDialog();
        // Launch call (will be handled by handleMessage on response)
        TrytonCall.login(this.login.getText().toString(),
                         this.password.getText().toString(),
                         new Handler(this));
    }

    /** Logout from an other activity. Brings back login screen. */
    public static void logout(Activity caller) {
        // Clear on server then on client
        TrytonCall.logout(Session.current.userId, Session.current.cookie);
        Session.current.clear();
        // Call back the login screen.
        // FLAG_ACTIVITY_CLEAR_TOP will erase all activities on top of it.
        Intent i = new Intent(caller, Start.class);
        awake = true;
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        caller.startActivity(i);
    }

    /** Show the loading dialog if not already shown. */
    public void showLoadingDialog() {
        if (this.loadingDialog == null) {
            this.loadingDialog = new ProgressDialog(this);
            this.loadingDialog.setIndeterminate(true);
            this.loadingDialog.setMessage(this.getString(R.string.login_logging_in));
            this.loadingDialog.show();
        }        
    }

    /** Hide the loading dialog if shown. */
    public void hideLoadingDialog() {
        if (this.loadingDialog != null) {
            this.loadingDialog.dismiss();
            this.loadingDialog = null;
        }
    }

    //////////////////
    // Menu section //
    //////////////////
    private static final int MENU_CONFIG_ID = 0;
    private static final int MENU_ABOUT_ID = 1;
    /** Called on menu initialization */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Create and add configuration entry
        MenuItem config = menu.add(Menu.NONE, MENU_CONFIG_ID, 0,
                                   this.getString(R.string.general_config));
        config.setIcon(android.R.drawable.ic_menu_preferences);
        // Create and add about entry
        MenuItem about = menu.add(Menu.NONE, MENU_ABOUT_ID, 10,
                                  this.getString(R.string.general_about));
        about.setIcon(android.R.drawable.ic_menu_info_details);
        return true;
    }

    /** Called on menu selection */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_CONFIG_ID:
            // Start the configuration activity
            Intent i = new Intent(this, Configure.class);
            this.startActivity(i);
            break;
        case MENU_ABOUT_ID:
            // Show about
            i = new Intent(this, About.class);
            this.startActivity(i);
        }
        return true;
    }

}
