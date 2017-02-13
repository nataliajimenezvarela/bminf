package es.uam.eps.bmi.search.index.lucene;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.jsoup.Jsoup;

import es.uam.eps.bmi.search.index.IndexBuilder;

public class LuceneIndexBuilder implements IndexBuilder {

	private IndexWriter idxwriter;
	private String indexPath;

	/**
	 * Crea un indice a partir de unos archivos HTML dados.
	 * 
	 * @param collectionPath
	 *            ruta de los archivos a indexar, puede ser una lista con una
	 *            web por linea que se descargara, o un zip/carpeta con archivos
	 *            html ya descargados
	 * @param indexPath
	 *            ruta del indice a crear
	 * 
	 */
	@Override
	public void build(String collectionPath, String indexPath) throws IOException {

		File collectionFile = new File(collectionPath);
		if (collectionFile.exists() == false) {
			throw new IOException();
		}

		/*
		 * instanciamos el IndexWriter para poder escribir los Documents en el
		 * Index
		 */
		Path path = Paths.get(indexPath);
		Directory indexDir = FSDirectory.open(path);

		this.idxwriter = new IndexWriter(indexDir, new IndexWriterConfig(new StandardAnalyzer()));

		/*
		 * leemos los archivos de disco y cargamos sus rutas, despues creamos un
		 * objeto Document con sus Fields a partir de cada ruta almacenada y lo
		 * indexamos
		 */

		/*
		 * Si es un zip, lo descomprimimos y leemos de manera normal los
		 * archivos html que obtengamos
		 */
		if (collectionPath.endsWith(".zip")) {

			ZipFile zipFile = new ZipFile(collectionPath);

			this.unzipFile(collectionPath, "tmp/");
			zipFile.close();

			File zipCollectionFile = new File("tmp/");
			for (File f : zipCollectionFile.listFiles()) {
				this.indexDocument((this.getDocument(Jsoup.parse(f, "UTF-8", f.getAbsolutePath()))));
			}

		}
		/* Si es un directorio de html's cargamos cada archivo */
		else if (collectionFile.isDirectory() == true) {

			for (File f : collectionFile.listFiles()) {
				if (f.isDirectory() == false) { // no entramos en directorios
					this.indexDocument((this.getDocument(Jsoup.parse(f, "UTF-8", f.getAbsolutePath()))));
				}
			}
		}
		/* Si es un txt con webs, descargamos cada web */
		else if (collectionFile.isFile() == true) {

			Stream<String> stream = Files.lines(Paths.get(collectionPath));

			stream.forEach(line -> {
				try {
					this.indexDocument((this.getDocument(Jsoup.connect(line).get())));
				} catch (IOException e) {
					e.printStackTrace();
				}
			});

			stream.close();
		}

		this.idxwriter.close();
	}

	/**
	 * Obtiene un Document de Apache Lucene a partir de uno de jsoup
	 * 
	 * @param d
	 *            documento jsoup valido
	 * 
	 * @return documento lucene con dos fields, la ruta en disco y el contenido
	 */
	private Document getDocument(org.jsoup.nodes.Document d) {

		if (d == null)
			return null;

		/* queremos que con el contenido se puedan crear TermVectors */
		FieldType ft = new FieldType(TextField.TYPE_STORED);
		ft.setStored(true);
		ft.setStoreTermVectors(true);
		ft.setStoreTermVectorOffsets(true);
		ft.setStoreTermVectorPayloads(true);
		ft.setStoreTermVectorPositions(true);

		/* Creamos los campos del documento */
		
		/* eliminamos tokens innecesarios y mayusculas */
		//String prueba = d.body().text().replaceAll("[{}*//()@;=+-<>]", "").toLowerCase();
				
		//Analyzer analyzer = new StandardAnalyzer();
		String normalized = d.body().text().replaceAll("[^A-Za-z]+", " ");
		 
		
		Field filePathField = new StringField("filepath", d.baseUri(), Store.YES);
		Field contentField = new Field("content", normalized, ft);

		/* Creamos el documento */
		Document document = new Document();

		document.add(contentField);
		document.add(filePathField);

		return document;
	}

	/**
	 * Indexa (escribe en disco) un documento usando el indexwriter propio del
	 * LuceneIndexBuilder
	 * 
	 * @param d
	 *            documento lucene a indexar
	 * 
	 * @throws IOException
	 *             si falla al añadir un documento al indice
	 */
	private void indexDocument(Document d) throws IOException {

		System.out.println("Indexing (" + this.idxwriter.numDocs() + "): " + d.getField("filepath").stringValue());

		/*
		 * No usamos addDocument, ya que agregaria elementos repetidos. Con
		 * updateDocument conseguimos que se actualicen pasandole el Field
		 * filepath
		 */
		idxwriter.updateDocument(new Term("filepath", d.getField("filepath").stringValue()), d);
	}

	/**
	 * Descomprime un archivo zip (solo los archivos al primer nivel)
	 * 
	 * @param zipPath
	 *            ruta del archivo zip
	 * @param destPath
	 *            ruta donde colocar los archivos descomprimidos
	 */
	private void unzipFile(String zipPath, String destPath) {

		// TODO comprobar parametros

		File dir = new File(destPath);
		if (!dir.exists()) // creacion de directorio destino
			dir.mkdirs();

		FileInputStream fis;
		int BUFFER = 2048;
		byte[] buffer = new byte[BUFFER];

		try {

			/* leemos el zip origen y extraemos la primera entrada */
			fis = new FileInputStream(zipPath);
			ZipInputStream zis = new ZipInputStream(fis);
			ZipEntry zEntry = zis.getNextEntry();

			/* iteramos sobre las entradas del archivo zip */
			while (zEntry != null) {

				String fileName = zEntry.getName();
				File destFile = new File(destPath + File.separator + fileName);

				FileOutputStream fos = new FileOutputStream(destFile);
				int len;
				while ((len = zis.read(buffer)) > 0) {
					fos.write(buffer, 0, len);
				}

				/* liberamos recursos */
				fos.close();
				zis.closeEntry();

				zEntry = zis.getNextEntry();
			}

			/* liberamos recursos finales */
			zis.closeEntry();
			zis.close();
			fis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}
