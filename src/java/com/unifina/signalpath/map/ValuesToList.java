package com.unifina.signalpath.map;

import com.unifina.signalpath.AbstractSignalPathModule;
import com.unifina.signalpath.ListOutput;
import com.unifina.signalpath.MapInput;

import java.util.ArrayList;

public class ValuesToList extends AbstractSignalPathModule {

	private MapInput in = new MapInput(this, "in");
	private ListOutput keys = new ListOutput(this, "keys");

	@Override
	public void sendOutput() {
		keys.send(new ArrayList(in.getValue().values()));
	}

	@Override
	public void clearState() {}
}
