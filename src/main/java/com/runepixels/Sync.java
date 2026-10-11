package com.runepixels;

public abstract class Sync
{
	private boolean requested;

	public void request()
	{
		requested = true;
	}

	boolean consumeRequest()
	{
		boolean r = requested;
		requested = false;
		return r;
	}

	public abstract boolean isEnabled();

	public abstract String getConfigKey();

	public abstract String getPath();

	public abstract String getDisplayName();

	public abstract void sync(long accountHash);

	public void reset()
	{
		requested = false;
	}
}
