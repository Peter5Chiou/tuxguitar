package app.tuxguitar.ui.swt.toolbar;

import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import app.tuxguitar.ui.resource.UIImage;
import app.tuxguitar.ui.swt.resource.SWTImage;
import app.tuxguitar.ui.toolbar.UIToolItem;

public class SWTToolItem extends SWTToolControl<ToolItem> implements UIToolItem {

	private UIImage image;
	private Image selectedImage;

	public SWTToolItem(ToolItem item, SWTToolBar parent) {
		super(item, parent);
		this.getControl().addListener(org.eclipse.swt.SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				getControl().getDisplay().asyncExec(new Runnable() {
					public void run() {
						if(!getControl().isDisposed()) {
							getParent().refreshImages();
						}
					}
				});
			}
		});
	}

	public void dispose() {
		this.getParent().dispose(this);
	}

	public void disposeControl() {
		this.disposeSelectedImage();
		this.getControl().dispose();
	}

	public boolean isControlDisposed() {
		return this.getControl().isDisposed();
	}

	public boolean isEnabled() {
		return this.getControl().isEnabled();
	}

	public void setEnabled(boolean enabled) {
		this.getControl().setEnabled(enabled);
	}

	public String getText() {
		return this.getControl().getText();
	}

	public void setText(String text) {
		this.getControl().setText(text);
	}

	public String getToolTipText() {
		return this.getControl().getToolTipText();
	}

	public void setToolTipText(String text) {
		this.getControl().setToolTipText(text);
	}

	public UIImage getImage() {
		return this.image;
	}

	public void setImage(UIImage image) {
		this.image = image;
		this.disposeSelectedImage();
		this.refreshImage();
	}

	public void refreshImage() {
		Image originalImage = this.image != null ? ((SWTImage) this.image).getHandle() : null;
		if(originalImage == null || !this.getControl().getSelection() || !this.isDarkBackground()) {
			this.getControl().setImage(originalImage);
			return;
		}

		if(this.selectedImage == null || this.selectedImage.isDisposed()) {
			this.selectedImage = this.createSelectedImage(originalImage);
		}
		this.getControl().setImage(this.selectedImage);
	}

	private boolean isDarkBackground() {
		org.eclipse.swt.graphics.RGB rgb = this.getParent().getControl().getBackground().getRGB();
		return ((rgb.red + rgb.green + rgb.blue) / 3) < 128;
	}

	private Image createSelectedImage(Image originalImage) {
		ImageData source = originalImage.getImageData();
		ImageData selected = new ImageData(source.width, source.height, source.depth, source.palette);
		for(int y = 0; y < source.height; y++) {
			for(int x = 0; x < source.width; x++) {
				int pixel = source.getPixel(x, y);
				RGB color = source.palette.getRGB(pixel);
				selected.setPixel(x, y, source.palette.getPixel(new RGB(255 - color.red, 255 - color.green, 255 - color.blue)));
			}
		}
		if(source.alphaData != null) {
			selected.alphaData = source.alphaData.clone();
		}
		selected.transparentPixel = source.transparentPixel;
		selected.alpha = source.alpha;
		return new Image(this.getControl().getDisplay(), selected);
	}

	private void disposeSelectedImage() {
		if(this.selectedImage != null && !this.selectedImage.isDisposed()) {
			this.selectedImage.dispose();
		}
		this.selectedImage = null;
	}
}
