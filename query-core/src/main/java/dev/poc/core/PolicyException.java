package dev.poc.core;
public class PolicyException extends RuntimeException {
 private static final long serialVersionUID=1L;
 public final String code;
 public PolicyException(String code,String message) { super(message);this.code=code; }
}
