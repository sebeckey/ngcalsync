package de.jakop.ngcalsync.oauth;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Scanner;


/**
 * User input receiver that prompts user to enter his input into the console prompt.
 *
 * @author Yaniv Inbar
 */
class PromptReceiver implements IUserInputReceiver {

	private final InputStream in;
	private final PrintStream out;

	/**
	 * 
	 */
	public PromptReceiver() {
		this(System.in, System.out);
	}

	/**
	 * 
	 * @param in
	 * @param out
	 */
	PromptReceiver(final InputStream in, final PrintStream out) {
		this.in = in;
		this.out = out;
	}

	@Override
	public String waitForUserInput(final String message) {
		String code;
		do {
			out.print(message);
			code = new Scanner(in).nextLine();
		} while (code.isEmpty());
		return code;
	}

}