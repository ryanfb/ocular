package edu.berkeley.cs.nlp.ocular.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import edu.berkeley.cs.nlp.ocular.data.textreader.BasicTextReader;
import edu.berkeley.cs.nlp.ocular.data.textreader.TextReader;
import edu.berkeley.cs.nlp.ocular.image.ImageUtils;
import edu.berkeley.cs.nlp.ocular.image.ImageUtils.PixelType;
import edu.berkeley.cs.nlp.ocular.image.Visualizer;
import edu.berkeley.cs.nlp.ocular.preprocessing.Binarizer;
import edu.berkeley.cs.nlp.ocular.preprocessing.Cropper;
import edu.berkeley.cs.nlp.ocular.preprocessing.LineExtractor;
import edu.berkeley.cs.nlp.ocular.preprocessing.Straightener;
import fileio.f;

/**
 * A dataset loader that reads files only as they are needed (and then stores
 * the contents in memory for later use).
 * 
 * @author Dan Garrette (dhg@cs.utexas.edu)
 */
public class LazyRawImageLoader implements ImageLoader {

	public static class LazyRawImageDocument implements ImageLoader.Document {
		private final File file;
		private final String inputPath;
		private final int lineHeight;
		private final double binarizeThreshold;
		private final boolean crop;

		private PixelType[][][] observations = null;
		private String[][] text = null;

		private TextReader textReader = new BasicTextReader();

		private String lineExtractionImageOutputPath = null;

		public LazyRawImageDocument(File file, String inputPath, int lineHeight, double binarizeThreshold, boolean crop, String lineExtractionImageOutputPath) {
			this.file = file;
			this.inputPath = inputPath;
			this.lineHeight = lineHeight;
			this.binarizeThreshold = binarizeThreshold;
			this.crop = crop;
			this.lineExtractionImageOutputPath = lineExtractionImageOutputPath;
		}

		public PixelType[][][] loadLineImages() {
			if (observations == null) {
				System.out.println("Extracting text line images from " + file);
				double[][] levels = ImageUtils.getLevels(f.readImage(file.getPath()));
				double[][] rotLevels = Straightener.straighten(levels);
				double[][] cropLevels = crop ? Cropper.crop(rotLevels, binarizeThreshold) : rotLevels;
				Binarizer.binarizeGlobal(binarizeThreshold, cropLevels);
				List<double[][]> lines = LineExtractor.extractLines(cropLevels);
				observations = new PixelType[lines.size()][][];
				for (int i = 0; i < lines.size(); ++i) {
					if (lineHeight >= 0) {
						observations[i] = ImageUtils.getPixelTypes(ImageUtils.resampleImage(ImageUtils.makeImage(lines.get(i)), lineHeight));
					}
					else {
						observations[i] = ImageUtils.getPixelTypes(ImageUtils.makeImage(lines.get(i)));
					}
				}

				if (lineExtractionImageOutputPath != null) {
					String fileParent = FileUtil.removeCommonPathPrefixOfParents(new File(inputPath), file)._2;
					String preext = FileUtil.withoutExtension(file.getName());
					String ext = FileUtil.extension(file.getName());
					String lineExtractionImagePath = lineExtractionImageOutputPath + "/" + fileParent + "/" + preext + "-line_extract." + ext;
					System.out.println("Writing line-extraction image to: " + lineExtractionImagePath);
					new File(lineExtractionImagePath).getParentFile().mkdirs();
					f.writeImage(lineExtractionImagePath, Visualizer.renderLineExtraction(observations));
				}
			}
			return observations;
		}

		public String[][] loadLineText() {
			if (text == null) {
				File textFile = new File(file.getPath().replaceAll("\\.[^.]*$", ".txt"));
				if (textFile.exists()) {
					System.out.println("Evaluation text found at " + textFile);
					List<List<String>> textList = new ArrayList<List<String>>();
					try {
						BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(textFile), "UTF-8"));
						while (in.ready()) {
							textList.add(textReader.readCharacters(in.readLine()));
						}
						in.close();
					}
					catch (IOException e) {
						throw new RuntimeException(e);
					}

					text = new String[textList.size()][];
					for (int i = 0; i < text.length; ++i) {
						List<String> line = textList.get(i);
						text[i] = line.toArray(new String[line.size()]);
					}
				}
				else {
					System.out.println("No evaluation text found at " + textFile);
				}
			}
			return text;
		}

		public String baseName() {
			return file.getPath();
		}

	}

	public static class LazyRawPdfImageDocument implements ImageLoader.Document {
		private final File pdfFile;
		private final int pageNumber; // starts at zero
		private final String inputPath;
		private final int lineHeight;
		private final double binarizeThreshold;
		private final boolean crop;

		private PixelType[][][] observations = null;

		private String lineExtractionImageOutputPath = null;

		public LazyRawPdfImageDocument(File pdfFile, int pageNumber, String inputPath, int lineHeight, double binarizeThreshold, boolean crop, String lineExtractionImageOutputPath) {
			this.pdfFile = pdfFile;
			this.pageNumber = pageNumber;
			this.inputPath = inputPath;
			this.lineHeight = lineHeight;
			this.binarizeThreshold = binarizeThreshold;
			this.crop = crop;
			this.lineExtractionImageOutputPath = lineExtractionImageOutputPath;
		}

		public PixelType[][][] loadLineImages() {
			if (observations == null) {
				System.out.println("Extracting text line images from " + pdfFile + ", page " + pageNumber);
				double[][] levels = ImageUtils.getLevels(PdfImageReader.readPdfPageAsImage(pdfFile, pageNumber));
				double[][] rotLevels = Straightener.straighten(levels);
				double[][] cropLevels = crop ? Cropper.crop(rotLevels, binarizeThreshold) : rotLevels;
				Binarizer.binarizeGlobal(binarizeThreshold, cropLevels);
				List<double[][]> lines = LineExtractor.extractLines(cropLevels);
				observations = new PixelType[lines.size()][][];
				for (int i = 0; i < lines.size(); ++i) {
					if (lineHeight >= 0) {
						observations[i] = ImageUtils.getPixelTypes(ImageUtils.resampleImage(ImageUtils.makeImage(lines.get(i)), lineHeight));
					}
					else {
						observations[i] = ImageUtils.getPixelTypes(ImageUtils.makeImage(lines.get(i)));
					}
				}

				if (lineExtractionImageOutputPath != null) {
					String fileParent = FileUtil.removeCommonPathPrefixOfParents(new File(inputPath), pdfFile)._2;
					String preext = new File(baseName()).getName();
					String ext = "jpg";
					
					String lineExtractionImagePath = lineExtractionImageOutputPath + "/" + fileParent + "/" + preext + "-line_extract." + ext;
					System.out.println("Writing line-extraction image to: " + lineExtractionImagePath);
					new File(lineExtractionImagePath).getParentFile().mkdirs();
					f.writeImage(lineExtractionImagePath, Visualizer.renderLineExtraction(observations));
				}
			}
			return observations;
		}

		public String[][] loadLineText() {
			return null;
		}

		public String baseName() {
			return FileUtil.withoutExtension(pdfFile.getPath()) + "_pdf_page" + String.format("%04d", pageNumber);
		}

	}

	private final String inputPath;
	private final int lineHeight;
	private final double binarizeThreshold;
	private final boolean crop;

	private String lineExtractionImageOutputPath = null;

	public LazyRawImageLoader(String inputPath, int lineHeight, double binarizeThreshold, boolean crop,
			String lineExtractionImageOutputPath) {
		this.inputPath = inputPath;
		this.lineHeight = lineHeight;
		this.binarizeThreshold = binarizeThreshold;
		this.crop = crop;
		this.lineExtractionImageOutputPath = lineExtractionImageOutputPath;
	}

	public List<Document> readDataset() {
		File dir = new File(inputPath);
		System.out.println("Reading data from [" + dir + "], which " + (dir.exists() ? "exists" : "does not exist"));
		List<File> dirList = FileUtil.recursiveFiles(dir);

		List<Document> docs = new ArrayList<Document>();
		for (File f : dirList) {
			if (f.getName().endsWith(".txt"))
				continue;
			if (f.getName().endsWith(".pdf")) {
				int numPages = PdfImageReader.numPagesInPdf(f);
				for (int pageNumber = 1; pageNumber <= numPages; ++pageNumber) {
					docs.add(new LazyRawPdfImageDocument(f, pageNumber, inputPath, lineHeight, binarizeThreshold, crop, lineExtractionImageOutputPath));
				}
			}
			else {
				docs.add(new LazyRawImageDocument(f, inputPath, lineHeight, binarizeThreshold, crop, lineExtractionImageOutputPath));
			}
		}
		return docs;
	}
}
