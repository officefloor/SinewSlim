package net.officefloor.sinewslim.intellij;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;

/**
 * A block inlay above a piece (or above the method, for its header): one line of text, then
 * buttons. Clicks are hit-tested against the buttons the last paint drew.
 */
final class SlimInlayRenderer implements EditorCustomElementRenderer {

	/** A button: what it says, and what it does when clicked. */
	record Button(String label, Runnable action) {
	}

	static final JBColor TEXT = new JBColor(new Color(0x59636e), new Color(0x9198a1));
	static final JBColor BUTTON = new JBColor(new Color(0x0969da), new Color(0x4493f8));

	private final String text;
	private final int indent;
	private final Color stripe;
	private final List<Button> buttons;
	private final List<Rectangle> hits = new ArrayList<>();

	/**
	 * @param indent the column the text starts at (the piece's own indentation)
	 * @param stripe the colour of the bar at the left, matching the piece's highlight; null for none
	 */
	SlimInlayRenderer(String text, int indent, Color stripe, List<Button> buttons) {
		this.text = text;
		this.indent = indent;
		this.stripe = stripe;
		this.buttons = buttons;
	}

	String text() {
		return text;
	}

	List<Button> buttons() {
		return buttons;
	}

	@Override
	public int calcWidthInPixels(@NotNull Inlay inlay) {
		Editor editor = inlay.getEditor();
		FontMetrics fm = editor.getContentComponent().getFontMetrics(font(editor));
		int w = x0(editor) + fm.stringWidth(text) + 16;
		for (Button b : buttons) {
			w += fm.stringWidth(b.label()) + 24;
		}
		return w;
	}

	@Override
	public int calcHeightInPixels(@NotNull Inlay inlay) {
		return inlay.getEditor().getLineHeight() + 4;
	}

	@Override
	public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle r, @NotNull TextAttributes attrs) {
		Editor editor = inlay.getEditor();
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			Font font = font(editor);
			g2.setFont(font);
			FontMetrics fm = g2.getFontMetrics();
			int x = r.x + x0(editor);
			int base = r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2;
			if (stripe != null) {
				g2.setColor(stripe);
				g2.fillRect(x - 6, r.y + 2, 3, r.height - 4);
			}
			g2.setColor(TEXT);
			g2.drawString(text, x, base);
			x += fm.stringWidth(text) + 16;
			hits.clear();
			for (Button b : buttons) {
				int w = fm.stringWidth(b.label()) + 16;
				Rectangle hit = new Rectangle(x, r.y + 2, w, r.height - 4);
				g2.setColor(BUTTON);
				g2.drawRoundRect(hit.x, hit.y, hit.width, hit.height - 1, 8, 8);
				g2.setColor(BUTTON);
				g2.drawString(b.label(), x + 8, base);
				hits.add(new Rectangle(hit.x - r.x, hit.y - r.y, hit.width, hit.height));
				x += w + 8;
			}
		} finally {
			g2.dispose();
		}
	}

	/** The button at a point relative to the inlay's bounds, or null. */
	Button buttonAt(int x, int y) {
		for (int i = 0; i < hits.size() && i < buttons.size(); i++) {
			if (hits.get(i).contains(x, y)) {
				return buttons.get(i);
			}
		}
		return null;
	}

	private int x0(Editor editor) {
		return editor.getInsets().left + indent * editor.getContentComponent().getFontMetrics(font(editor)).charWidth(' ');
	}

	private static Font font(Editor editor) {
		Font f = editor.getColorsScheme().getFont(EditorFontType.ITALIC);
		return f.deriveFont(f.getSize2D() * 0.92f);
	}
}
