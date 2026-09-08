package app.tuxguitar.app.view.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import app.tuxguitar.app.system.config.TGConfigKeys;
import app.tuxguitar.app.system.config.TGConfigManager;
import app.tuxguitar.app.system.icons.TGSkinManager;
import app.tuxguitar.app.system.properties.TGPropertiesUIUtil;
import app.tuxguitar.app.ui.TGApplication;
import app.tuxguitar.event.TGEvent;
import app.tuxguitar.event.TGEventListener;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UIDisposeEvent;
import app.tuxguitar.ui.event.UIDisposeListener;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.resource.UIColorModel;
import app.tuxguitar.ui.widget.UIControl;
import app.tuxguitar.ui.widget.UIContainer;
import app.tuxguitar.ui.widget.UIWindow;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.properties.TGProperties;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

public class TGDialogSkinManager implements TGEventListener {

	private TGContext context;
	private List<UIWindow> windows;

	private TGDialogSkinManager(TGContext context) {
		this.context = context;
		this.windows = new ArrayList<UIWindow>();
		TGSkinManager.getInstance(this.context).addLoader(this);
	}

	public void register(final UIWindow window) {
		if(window == null || window.isDisposed() || this.windows.contains(window)) {
			return;
		}

		this.windows.add(window);
		window.addDisposeListener(new UIDisposeListener() {
			public void onDispose(UIDisposeEvent event) {
				windows.remove(window);
			}
		});
		this.applySkinColors(window);
	}

	public void applySkinColors(UIWindow window) {
		if(window == null || window.isDisposed()) {
			return;
		}

		UIFactory uiFactory = TGApplication.getInstance(this.context).getFactory();
		TGConfigManager config = TGConfigManager.getInstance(this.context);
		TGProperties skinProperties = TGSkinManager.getInstance(this.context).getCurrentSkinProperties();
		UIColorModel backgroundModel = TGPropertiesUIUtil.getColorModelValue(this.context, skinProperties, "color.background", config.getColorModelConfigValue(TGConfigKeys.COLOR_BACKGROUND));
		UIColorModel foregroundModel = TGPropertiesUIUtil.getColorModelValue(this.context, skinProperties, "color.foreground", config.getColorModelConfigValue(TGConfigKeys.COLOR_FOREGROUND));
		this.applySkinColors(window, uiFactory.createColor(backgroundModel), uiFactory.createColor(foregroundModel));
		window.layout();
		window.redraw();
	}

	private void applySkinColors(UIControl control, UIColor background, UIColor foreground) {
		control.setBgColor(background);
		control.setFgColor(foreground);
		control.redraw();
		if(control instanceof UIContainer) {
			for(UIControl child : ((UIContainer) control).getChildren()) {
				this.applySkinColors(child, background, foreground);
			}
		}
	}

	private void refresh() {
		Iterator<UIWindow> iterator = this.windows.iterator();
		while(iterator.hasNext()) {
			UIWindow window = iterator.next();
			if(window == null || window.isDisposed()) {
				iterator.remove();
			}
			else {
				this.applySkinColors(window);
			}
		}
	}

	public void processEvent(TGEvent event) {
		this.refresh();
	}

	public static TGDialogSkinManager getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, TGDialogSkinManager.class.getName(), new TGSingletonFactory<TGDialogSkinManager>() {
			public TGDialogSkinManager createInstance(TGContext context) {
				return new TGDialogSkinManager(context);
			}
		});
	}
}
