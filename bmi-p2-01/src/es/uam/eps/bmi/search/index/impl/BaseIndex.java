package es.uam.eps.bmi.search.index.impl;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import es.uam.eps.bmi.search.index.AbstractIndex;
import es.uam.eps.bmi.search.index.Config;
import es.uam.eps.bmi.search.index.structure.Posting;
import es.uam.eps.bmi.search.index.structure.impl.RAMPostingsList;

/**
 * Representa un Indice base para los indices implementados, agrupando las
 * funciones basicas para evitar repeticion de codigo.
 * 
 * @author Jorge Cifuentes
 * @author Alejandro Martin
 *
 * @param <K>
 *            Clase que representa el termino. Usualmente sera String, pero
 *            puede ser cualquiera que extienda de CharSequence.
 * @param <V>
 *            Tipo de almacenamiento para la lista de Postings (la propia lista
 *            toda en memoria, un offset al archivo de postings en disco,
 *            etc...).
 */
public abstract class BaseIndex<K extends CharSequence, V> extends AbstractIndex {

	private int numDocs;
	private List<String> paths;
	protected Map<K, V> dictionary;

	/**
	 * Dado un indice, lo carga al dictionary, de acuerdo a la estrategia
	 * seguida para los parametros <K, V>.
	 * 
	 * @param indexPath
	 *            ruta del indice almacenado por algun Builder
	 */
	protected abstract void deserializeIndex(String indexPath);

	public Map<K, V> getDictionary() {
		return this.dictionary;
	}

	public BaseIndex(String path) throws IOException {
		super(path);
	}

	@Override
	public void load(String path) throws IOException {

		this.dictionary = new HashMap<K, V>();
		this.paths = new ArrayList<>();

		this.indexFolder = path;

		// cargamos el indice
		this.deserializeIndex(this.indexFolder);

		// cargamos los paths
		BufferedReader br2 = null;

		try {
			String sCurrentLine;

			br2 = new BufferedReader(new FileReader(path + Config.pathsFileName));

			while ((sCurrentLine = br2.readLine()) != null) {
				this.paths.add(sCurrentLine);
				this.numDocs++; // numero de docs leidos
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		// por ultimo, cargamos las normas
		this.loadNorms(path);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<String> getAllTerms() throws IOException {
		return (Collection<String>) this.dictionary.keySet();
	}

	@Override
	public int numDocs() {
		return this.numDocs;
	}

	@Override
	public long getTotalFreq(String term) throws IOException {

		// numero de veces que aparece "word" en todos los documentos
		RAMPostingsList pl = (RAMPostingsList) this.getPostings(term);

		int res = 0;
		for (Posting p : pl) {
			res += p.getFreq();
		}

		return res;
	}

	@Override
	public long getDocFreq(String term) throws IOException {
		// numero de documentos donde aparece term: longitud de su posting list
		return this.getPostings(term).size();
	}

	@Override
	public String getDocPath(int docID) throws IOException {
		return this.paths.get(docID);
	}

	@Override
	public double getDocNorm(int docID) throws IOException {
		return this.docNorms[docID];
	}

}
