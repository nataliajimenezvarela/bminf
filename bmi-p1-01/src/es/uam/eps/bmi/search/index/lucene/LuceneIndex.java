package es.uam.eps.bmi.search.index.lucene;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.jsoup.nodes.Document;

import es.uam.eps.bmi.search.index.AbstractIndex;
import es.uam.eps.bmi.search.index.NoIndexException;
import es.uam.eps.bmi.search.index.freq.FreqVector;
import es.uam.eps.bmi.search.index.freq.lucene.LuceneFreqVector;
import es.uam.eps.bmi.search.index.freq.lucene.LuceneFreqVectorIterator;

public class LuceneIndex extends AbstractIndex {

	private IndexReader idxReader;
	private String indexPath;

	public LuceneIndex(String path) throws IOException {
		super(path);
	}

	@Override
	public void load(String iPath) throws IOException {

		Path path = Paths.get(iPath);
		this.indexPath = iPath;

		/*
		 * Creamos un FSDirectory a partir de la ruta pasada y lo abrimos en el
		 * indexReader
		 */

		Directory directory = FSDirectory.open(path);

		if (DirectoryReader.indexExists(directory) == false) {
			throw new NoIndexException("El indice no existe");
		}

		this.idxReader = DirectoryReader.open(directory);

	}

	private List<String> getRawTerms() throws NoIndexException {

		if (this.idxReader == null) {
			throw new NoIndexException(this.indexPath);
		}
		
		TermEnum termEnum = idxReader.terms(); 
		while (termEnum.next()) { 
		    Term term = termEnum.term(); 
		    System.out.println(term.text()); 
		}

		List<String> ret = new ArrayList<String>();

		for (int i = 0; i < idxReader.numDocs(); i++) {

			// TODO if (idxReader.isDeleted(i))
			// continue;

			org.apache.lucene.document.Document doc;
			try {
				doc = idxReader.document(i);
				String[] s = doc.getValues("content");

				for (String s1 : s) {
					ret.addAll(new ArrayList<String>(Arrays.asList(s1.split(" "))));
				}

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/* eliminamos tokens innecesarios y mayusculas */
		//List<String> res = ret.stream().map(str -> str.replaceAll("[{}*//()@;]", "").toLowerCase())
		//		.filter(str -> str.length() > 0).sorted(String::compareTo).collect(Collectors.toList());

		return ret;
	}

	@Override
	public List<String> getAllTerms() {
		try {
			return this.getRawTerms().stream().distinct().collect(Collectors.toList());
		} catch (NoIndexException e) {
			e.printStackTrace();
			return null;
		}

	}

	@Override
	public FreqVector getDocVector(int docID) throws IOException {

		Terms t = this.idxReader.getTermVector(docID, "content");
		LuceneFreqVector lfv = new LuceneFreqVector(t);

		return lfv;
	}

	@Override
	public String getDocPath(int docID) throws IOException {
		// devolvemos la ruta fisica del documento pasado
		org.apache.lucene.document.Document d = this.idxReader.document(docID);
		return d.getValues("filepath")[0];
		
	}

	@Override
	public long getTermFreq(String word, int docID) throws IOException {

		LuceneFreqVector fvector = new LuceneFreqVector(this.idxReader.getTermVector(docID, "content"));
		LuceneFreqVectorIterator fiter = (LuceneFreqVectorIterator) fvector.iterator();

		// numero de veces que aparece "word" en un documento especifico
		return fiter.getFreq(word);
	}

	@Override
	public int getTermTotalFreq(String word) throws IOException {
		Term t = new Term("content", word);

		// numero de veces que aparece "word" en todos los documentos
		return (int) this.idxReader.totalTermFreq(t);
	}

	@Override
	public long getTermDocFreq(String word) throws IOException {
		Term t = new Term("content", word);

		// numero de documentos donde aparece "word"
		return this.idxReader.docFreq(t);
	}

	public IndexReader getIndexReader() {
		return idxReader;
	}

}
