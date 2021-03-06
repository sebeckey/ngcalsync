package de.jakop.ngcalsync.util.file;

import java.io.File;


/**
 * Provides access to the settings directory and its files.
 * 
 * @author fjakop
 *
 */
public interface IFileAccessor {

	/**
	 * 
	 * @param name
	 * @return the {@link File} for the name from the settings directory
	 */
	public abstract File getFile(final String name);

}