package com.austinv11.discordbotinstaller;

public class GeneralException extends Exception {
	
	public ErrorCodes code;
	
	public GeneralException(ErrorCodes errorCode) {
		this.code = errorCode;
	}
}
