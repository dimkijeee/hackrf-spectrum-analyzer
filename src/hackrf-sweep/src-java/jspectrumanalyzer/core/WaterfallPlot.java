package jspectrumanalyzer.core;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import javax.swing.JPanel;

import org.jfree.chart.ChartPanel;

public class WaterfallPlot extends JPanel
{
	/**
	 * 
	 */
	private static final long	serialVersionUID		= 3249110968962287324L;
	private BufferedImage		bufferedImages[]		= new BufferedImage[2];
	private int					chartXOffset			= 0, chartWidth = 100;
	private int					drawIndex				= 0;
	private EMA					fps						= new EMA(3);
	private int					fpsRenderedFrames		= 0;
	private long				lastFPSRecalculated		= 0;
	private ColorPalette		palette					= new HotIronBluePalette();
	private String				renderingInfo			= "";

	private int					screenWidth;

	private double				spectrumPaletteSize		= 65;

	private double				spectrumPaletteStart	= -90;
	/**
	 * stores max value in pixel
	 */
	private float 				drawMaxBuffer[];
	
	public WaterfallPlot(ChartPanel chartPanel, int maxHeight)
	{
		setPreferredSize(new Dimension(100, 200));
		setMinimumSize(new Dimension(100, 200));

		addComponentListener(new ComponentAdapter()
		{
			@Override public void componentResized(ComponentEvent e)
			{
				setHistorySize(getHeight());
			}
		});

		screenWidth = (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth();
		drawMaxBuffer	= new float[screenWidth];
		bufferedImages[0] = new BufferedImage(screenWidth, maxHeight, BufferedImage.TYPE_3BYTE_BGR);
		bufferedImages[1] = new BufferedImage(screenWidth, maxHeight, BufferedImage.TYPE_3BYTE_BGR);
	}

	private Rectangle2D.Float rect = new Rectangle2D.Float(0f, 0f, 1f, 1f);
	/**
	 * Adds new data to the waterfall plot and renders it
	 * @param spectrum
	 */
	public synchronized void addNewData(DatasetSpectrum spectrum)
	{
		int size = spectrum.spectrumLength();
		double startFreq = spectrum.getFreqStartMHz() * 1000000d;
		double freqRange = (spectrum.getFreqStopMHz() - spectrum.getFreqStartMHz()) * 1000000d;
		double width = bufferedImages[0].getWidth();
		double spectrumPalleteMax = spectrumPaletteStart + spectrumPaletteSize;

		/**
		 * shift image by one pixel down
		 */
		BufferedImage previousImage = bufferedImages[drawIndex];
		drawIndex = (drawIndex + 1) % 2;
		Graphics2D g = bufferedImages[drawIndex].createGraphics();
		g.drawImage(previousImage, 0, 1, null);
		g.setColor(Color.black);
		g.fillRect(0, 0, (int) width, 1);

		float binWidth = (float) (spectrum.getFFTBinSizeHz() / freqRange * width);
		rect.x	= 0;
		rect.y	= 0;
		rect.height	= 0;
		rect.width = binWidth;

		float minimumValueDrawBuffer	= -150;
		Arrays.fill(drawMaxBuffer, minimumValueDrawBuffer);
		
		/**
		 * draw in two passes - first determines maximum power for the pixel, second draws it
		 */
		for (int i = 0; i < size; i++)
		{
			double freq = spectrum.getFrequency(i);
			double power = spectrum.getPower(i);
			double percentageFreq = (freq - startFreq) / freqRange;
			double percentagePower = power < spectrumPaletteStart ? 0 : power > spectrumPalleteMax ? 1 : (power - spectrumPaletteStart) / spectrumPaletteSize;
			int pixelX = (int) Math.round(width * percentageFreq);
			pixelX	= pixelX >= drawMaxBuffer.length ? drawMaxBuffer.length-1 : pixelX < 0 ? 0 : pixelX;
			if (percentagePower > drawMaxBuffer[pixelX])
				drawMaxBuffer[pixelX]	= (float) percentagePower; 
		}
		
		/**
		 * fill in pixels that do not have power with last bin's color
		 */
		Color lastValidColor	= palette.getColor(0);
		for (int x = 0; x < drawMaxBuffer.length; x++)
		{
			Color color;
			if (drawMaxBuffer[x] == minimumValueDrawBuffer)
				color	= lastValidColor;
			else	
			{
				color = palette.getColorNormalized(drawMaxBuffer[x]);
				lastValidColor	= color;
			}
			rect.x = x;
			g.setColor(color);
			g.draw(rect);
		}
		
		renderingInfo = String.format("No. of FFT bins: %d%s / %.1ffps", size >= 10000 ? size / 1000 : size, size >= 10000 ? "k" : "", fps.getEma());
		fpsRenderedFrames++;
		if (System.currentTimeMillis() - lastFPSRecalculated > 1000)
		{
			double rawfps = fpsRenderedFrames / ((System.currentTimeMillis() - (double) lastFPSRecalculated) / 1000d);
			fps.addNewValue(rawfps);
			lastFPSRecalculated = System.currentTimeMillis();
			fpsRenderedFrames = 0;
		}
		g.dispose();
		repaint();
	}

	/**
	 * Draws color palette into given area from bottom (0%) to top (100%)
	 * @param g
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 */
	public void drawScale(Graphics2D g, int x, int y, int w, int h)
	{
		g = (Graphics2D) g.create(x, y, w, h);
		int step = 3;
		for (int i = 0; i < h; i += step)
		{
			Color c = palette.getColorNormalized(1 - (double) i / h);
			g.setColor(c);
			g.fillRect(0, i, w, step);
		}
	}

	public int getHistorySize()
	{
		return bufferedImages[0].getHeight();
	}

	public double getSpectrumPaletteSize()
	{
		return spectrumPaletteSize;
	}

	public double getSpectrumPaletteStart()
	{
		return spectrumPaletteStart;
	}

	public void setDrawingOffsets(int xOffsetLeft, int width)
	{
		this.chartXOffset = xOffsetLeft;
		this.chartWidth = width;
	}

	public synchronized void setHistorySize(int historyInPixels)
	{
		BufferedImage bufferedImages[] = new BufferedImage[2];
		bufferedImages[0] = new BufferedImage(screenWidth, historyInPixels, BufferedImage.TYPE_3BYTE_BGR);
		bufferedImages[1] = new BufferedImage(screenWidth, historyInPixels, BufferedImage.TYPE_3BYTE_BGR);
		copyImage(this.bufferedImages[0], bufferedImages[0]);
		copyImage(this.bufferedImages[1], bufferedImages[1]);
		this.bufferedImages = bufferedImages;
	}

	public void setSpectrumPaletteSize(int dB)
	{
		this.spectrumPaletteSize = dB;
	}

	/**
	 * Sets start and end of the color scale
	 * @param minFreqency
	 * @param maxFrequency
	 */
	public void setSpectrumPaletteStart(int dB)
	{
		this.spectrumPaletteStart = dB;
	}

	private void copyImage(BufferedImage src, BufferedImage dst)
	{
		Graphics2D g = dst.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
	}

	@Override protected void paintComponent(Graphics arg0)
	{
		Graphics2D g = (Graphics2D) arg0;
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		int w = chartWidth;
		int h = getHeight();
		g.setColor(Color.black);
		g.fillRect(0, 0, getWidth(), getHeight());

		g.drawImage(bufferedImages[drawIndex], chartXOffset, 0, w, h, null);
		g.setColor(Color.white);
		g.drawString(renderingInfo, chartXOffset + w - 150, h - 20);
	}
}
