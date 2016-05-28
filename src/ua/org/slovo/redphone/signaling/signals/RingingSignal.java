/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ua.org.slovo.redphone.signaling.signals;

/**
 * A signal which indicates that the endpoint has
 * received an "initiate" push notification, and that
 * the endpoint device is ringing.
 *
 * @author Moxie Marlinspike
 *
 */

public class RingingSignal extends Signal {

  private final long sessionId;

  public RingingSignal(String localNumber, String password, long counter, long sessionId) {
    super(localNumber, password, counter);
    this.sessionId = sessionId;
  }

  @Override
  protected String getMethod() {
    return "RING";
  }

  @Override
  protected String getLocation() {
    return "/session/" + sessionId;
  }

  @Override
  protected String getBody() {
    return null;
  }

}
