package ua.org.slovo.redphone.signaling;

import java.io.IOException;

public class UnauthorizedException extends IOException {
  public UnauthorizedException(String s) {
    super(s);
  }
}
