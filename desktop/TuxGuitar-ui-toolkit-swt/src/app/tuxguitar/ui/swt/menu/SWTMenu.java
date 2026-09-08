package app.tuxguitar.ui.swt.menu;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import app.tuxguitar.ui.UIComponent;
import app.tuxguitar.ui.menu.UIMenu;
import app.tuxguitar.ui.menu.UIMenuActionItem;
import app.tuxguitar.ui.menu.UIMenuCheckableItem;
import app.tuxguitar.ui.menu.UIMenuItem;
import app.tuxguitar.ui.menu.UIMenuSubMenuItem;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.swt.resource.SWTColor;
import app.tuxguitar.ui.swt.widget.SWTEventReceiver;

public class SWTMenu extends SWTEventReceiver<Menu> implements UIMenu {

	private static final String MENU_LIST_KEY = SWTMenu.class.getName() + ".menus";
	private static final String MENU_BACKGROUND_KEY = SWTMenu.class.getName() + ".background";
	private static final String MENU_FOREGROUND_KEY = SWTMenu.class.getName() + ".foreground";
	private static final String DARK_THEME_KEY = "org.eclipse.swt.internal.win32.useDarkModeExplorerTheme";
	private static final String MENU_BAR_BACKGROUND_KEY = "org.eclipse.swt.internal.win32.menuBarBackgroundColor";
	private static final String MENU_BAR_FOREGROUND_KEY = "org.eclipse.swt.internal.win32.menuBarForegroundColor";
	private static final String MENU_BAR_BORDER_KEY = "org.eclipse.swt.internal.win32.menuBarBorderColor";

	private List<UIMenuItem>  menuItems;

	public SWTMenu(Shell shell, int style) {
		super(new Menu(shell, style));

		this.menuItems = new ArrayList<UIMenuItem>();
		register(this);
	}

	public static void setDefaultColors(Display display, UIColor background, UIColor foreground) {
		if (!"win32".equals(SWT.getPlatform())) {
			return;
		}

		if (!(background instanceof SWTColor) || !(foreground instanceof SWTColor)) {
			return;
		}

		Color backgroundColor = ((SWTColor) background).getHandle();
		Color foregroundColor = ((SWTColor) foreground).getHandle();
		display.setData(MENU_BACKGROUND_KEY, backgroundColor);
		display.setData(MENU_FOREGROUND_KEY, foregroundColor);
		display.setData(DARK_THEME_KEY, Boolean.valueOf(isDark(backgroundColor)));
		display.setData(MENU_BAR_BACKGROUND_KEY, backgroundColor);
		display.setData(MENU_BAR_FOREGROUND_KEY, foregroundColor);
		display.setData(MENU_BAR_BORDER_KEY, backgroundColor);

		// SWT reads these display data values when native menus are created.
		// Do not update existing Menu handles here: Windows recreates submenu
		// handles while they are opened, and forcing the private color methods
		// at that point causes the menu to alternate between light and dark.
	}

	private static boolean isDark(Color color) {
		return ((color.getRed() * 299) + (color.getGreen() * 587) + (color.getBlue() * 114)) < 128000;
	}

	@SuppressWarnings("unchecked")
	private static List<SWTMenu> getMenus(Display display) {
		List<SWTMenu> menus = (List<SWTMenu>) display.getData(MENU_LIST_KEY);
		if (menus == null) {
			menus = new ArrayList<SWTMenu>();
			display.setData(MENU_LIST_KEY, menus);
		}
		return menus;
	}

	private static Color getDefaultColor(Display display, String key) {
		return (Color) display.getData(key);
	}

	private static void register(SWTMenu menu) {
		getMenus(menu.getControl().getDisplay()).add(menu);
	}

	public Integer getItemCount() {
		return this.menuItems.size();
	}

	public UIMenuItem getItem(int index) {
		return (index >= 0 && index < this.menuItems.size() ? this.menuItems.get(index) : null);
	}

	public List<UIMenuItem> getItems() {
		return new ArrayList<UIMenuItem>(this.menuItems);
	}

	public UIComponent createSeparator() {
		MenuItem menuItem = new MenuItem(this.getControl(), SWT.SEPARATOR);

		return this.append(new SWTMenuItem(menuItem, this));
	}

	public UIMenuActionItem createActionItem() {
		MenuItem menuItem = new MenuItem(this.getControl(), SWT.PUSH);

		return this.append(new SWTMenuActionItem(menuItem, this));
	}

	public UIMenuCheckableItem createCheckItem() {
		MenuItem menuItem = new MenuItem(this.getControl(), SWT.CHECK);

		return this.append(new SWTMenuCheckableItem(menuItem, this));
	}

	public UIMenuCheckableItem createRadioItem() {
		MenuItem menuItem = new MenuItem(this.getControl(), SWT.RADIO);

		return this.append(new SWTMenuCheckableItem(menuItem, this));
	}

	public UIMenuSubMenuItem createSubMenuItem() {
		MenuItem menuItem = new MenuItem(this.getControl(), SWT.CASCADE);

		return this.append(new SWTMenuSubMenuItem(menuItem, this));
	}

	@SuppressWarnings("unchecked")
	public <T extends UIMenuItem> T append(UIMenuItem item) {
		this.menuItems.add(item);

		return (T) item;
	}

	public void dispose(SWTMenuItem item) {
		if( this.menuItems.contains(item)) {
			this.menuItems.remove(item);
		}
		item.getControl().dispose();
	}

	public void dispose() {
		getMenus(this.getControl().getDisplay()).remove(this);
		this.getControl().dispose();
	}

	public boolean isDisposed() {
		return this.getControl().isDisposed();
	}
}
