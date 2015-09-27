package msra.nlp.el;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Pair.ByFirstReversePairComparator;
import msra.nlp.kb.Freebase;
import msra.nlp.kb.Surname;
import msra.nlp.kb.Wikipedia;
import msra.nlp.kb.Zoon;
import pml.collection.util.Unique;
import pml.string.util.Format;

public class NameScore 
{
	protected Wikipedia wiki = null;
	protected Freebase freebase = null;
	protected Surname surname = null;
	protected Zoon zoon = null;
	protected String text = null;
	protected List<Map> mentions = null; 
	protected Map query = null;
	
	/**
	 * set the properties of the decoref
	 * @param props
	 */
	public NameScore(Map props)
	{
		if(props.get("wiki")!=null)
		{
			this.wiki =  (Wikipedia) props.get("wiki");
		}
		else
		{
			this.wiki = new Wikipedia();
		}
		if(props.get("freebase")!=null)
		{
			this.freebase = (Freebase) props.get("freebase");
		}
		else
		{
			this.freebase = new Freebase();
		}
		if(props.get("surname")!=null)
		{
			this.surname =  (Surname) props.get("surname");
		}
		else
		{
			this.surname = new Surname();
		}
		if(props.get("zoon")!=null)
		{
			this.zoon =  (Zoon) props.get("zoon");
		}
		else
		{
			this.zoon = new Zoon();
		}
	}

//	Name score
	

private List<Pair<Float, Map>> NameScore() 
{
	String
	// get query information
	int begin = (int) this.query.get("begin");
	int end = (int) this.query.get("end");
	int span = 5;
	int from = begin-span;
	int to = end+span;
	if(from<0)
	{
		from = 0;
	}
	if(to>text.length())
	{
		to = text.length();
	}
	String context = this.text.substring(from, to); 	// Get the context text of the query
	context = CheckName(context);
	context = Trim(context); 										// delete \r \n \t
	String name = (String) this.query.get("name");
	name = Format.Zhf2Zhj(name);							// convert traditional chinese into simple chinese
	String temp = CheckName(name); 								// convert 科比？布莱恩特 to 科比·布莱恩特
	context = context.replace(name, temp);
	
	name = temp;
	 List<Pair<Float, Map>> pairs = NameScore(name,context);
	 if(pairs==null)
	 {
		 this.mentions = (List<Map>) this.ner.QueryText(text, (String) query.get("docid"));
		 this.dcoref.DcorefBySurname(this.text, this.mentions, this.query); // the query object may have been updated
		 if(!name.equals(this.query.get("name"))) // if query hava been changed by dcoref
		 {
			 context = context.replace(name, temp);
			 name = temp;
			 pairs = NameScore(name,context);
		 }
	 }
	return pairs;
}

/**
 * calculate the score of each entity by name comparing 
 * @param name
 * @param context
 * 
 * @return
 * 			A list of pairs with each pair store one candidate's matching condition.
 * 			The first element of a pair is the score it get in surface matching and the second
 * 			element is a map with fields: "index": the index of the entity,"longName": if the surface match is long-name match
 * 			if so, it should be offered award in candidate ranking. 
 */
private List<Pair<Float, Map>> NameScore(String name, String context)
{

	List<Pair<Float,Map>> scores = new ArrayList<>();
	float maxScore = 0;
	float score = 0;
	float thresh = (float) 0.7;
	List<String> alias; 
	@SuppressWarnings("rawtypes")
	Map<String,List> map;
	String  redirectTitle= null;

	
	// get ambiguities of the query mention
	List<Pair<String, String>> ambiguities = this.wiki.GetAmbigItems(name);
	if(ambiguities==null)
	{
		String redirectedName = this.wiki.GetRedirectTitle(name);
		if(redirectedName!=null)
		{
			ambiguities = this.wiki.GetAmbigItems(redirectedName);
		}
	}
	// delete name's and context's dot
	name = DeleteDot(name);
	context = DeleteDot(context);
	List<String> titles = new ArrayList<>();
	Pair<Float, Map> pair;
	Map m;
	Integer times = 0;
	Integer tm = 0;
	// for each title in freebase
	for(int i=0;i<freebase.GetSize();i++)
	{
		score = 0;
		maxScore = 0;
		titles = freebase.GetName(i);
		pair = new Pair<>();
		m = new HashMap<>();
		m.put("index", i);
		m.put("longName", false);
		// match name with original entity title
		for(String title:titles)
		{
			
			score = GetWordSimilarity(name, DeleteDot(title));
			if(score>maxScore)
			{
				maxScore = score;
			}
			if(maxScore==1)
			{
				break;
			}
			// get title's redirected title(then get the redirected title's redirect titles)
			redirectTitle = this.wiki.GetRedirectTitle(title); // save dot to find redirects
			if(redirectTitle!=null)
			{
				title = redirectTitle;
			}
			// get title linked times
			
			// match name with entity's alias
			map = GetEntityAlias(title);
			// delete title's dot
			title = DeleteDot(title);
			List<String> redirects = map.get(2);
			if(redirects!=null)
			{
				for (String redirect: redirects)
				{
					// delete redirect's dot
					redirect = DeleteDot(redirect);
					if(context.contains(redirect) && redirect.contains(name))
					{
						maxScore = 1;
						if(!name.contains(redirect))
						{
							m.put("longName", true);
						}
						break;
					}
				}
			}
			if(maxScore==1)
			{
				break;
			}
			List<String> anchors = map.get(3);
			if(anchors!=null)
			{
				for (String anchor: anchors)
				{
					// delete anchor dot
					anchor = DeleteDot(anchor);
					if(context.contains(anchor) && anchor.contains(name))
					{
						if(!name.contains(anchor))
						{
							m.put("longName", true);
						}
						maxScore = 1;
						break;
					}
				}
			}
			if(maxScore==1)
			{
				break;
			}
			// match mention's disambiguous items
			if(ambiguities!=null)
			{
				for(Pair ambiguity: ambiguities)
				{
					ambiguity.first = DeleteDot((String) ambiguity.first);
					if(title.equals(Trim((String) ambiguity.first)))
					{
						maxScore = 1;
						break;
					}
				}
			}
		}
		pair.first = maxScore;
		pair.second = m;
		scores.add(pair);
	}
	// choose candidates with score higher than the threshold
	ByFirstReversePairComparator<Float, Map> comparator = new ByFirstReversePairComparator<>();
	Collections.sort(scores,comparator);
	int i=0;
	for(i=0;i<maxCandidates;i++)
	{
		if(scores.get(i).first<thresh )
		{
			break;
		}
	}
	if(i==0)
	{
		return null;
	}
	return scores.subList(0, i);
}

private String CheckName(String name) 
{
	
	name = name.replaceAll("[^\\p{L}”“\"]", "\u00b7");
	return name;
}

private String Trim(String context)
{
	context = context.replace("\r", "");
	context = context.replace("\n", "");
	context = context.replace("\t","");
	return context;
}

/**
 * delete the "." in the text 
 * @param text
 * @return
 */
private String DeleteDot(String text)
{
	return text.replace("\u00b7", "");
}

public static float GetWordSimilarity(String mention,String title)
{
	
	if(mention==null || title==null)
	{
		return 0;
	}
	String w1;
	String w2;
	if(mention.length()>=title.length())
	{
		w1 = mention;
		w2 = title;
	}
	else
	{
		w1 = title;
		w2 = mention;
	}
	
	int matchNum = 0;
	int maxMatchNum = 0;
	int L1 = w1.length();
	int L2 = w2.length();
	int L3 = 0;
	int i=0;
	
	while(L1>0)
	{
		L3 = Math.min(L1--, L2);
		matchNum = MatchWord(w1.substring(i,i+L3), w2.substring(0,L3));
		i++;
		if(matchNum>maxMatchNum)
		{
			maxMatchNum = matchNum;
		}
	}
	return (float) 1.0*maxMatchNum/w1.length();
}

private static int MatchWord(String w1, String w2)
{
	int matchNum = 0;
	int size = Math.min(w1.length(), w2.length());
	for(int i=0;i<size;i++)
	{
		if(w1.charAt(i)==w2.charAt(i))
		{
			matchNum++;
		}
	}
	return matchNum;
}

//		Get alias

/**
 * get the alias of the given mention represented by the "title"
 * 
 * @param title
 * 				The title of the given page
 * @return
 * 				A map with 0,1,2fields storing the orginal name, ambiguous titles of the mention, redirected title of the mention title
 */
private Map GetMentionAlias(String name)
{

	Map alias = new HashMap<>(); 
	alias.put(0,name);
	String redirect = wiki.GetRedirectTitle(name);
	if(redirect!=null)
	{
		alias.put(1,redirect);
	}
	List<Pair<String, String>> ambiguities = wiki.GetAmbigItems(name); 
	if(ambiguities!=null)
	{
		alias.put(2, ambiguities);
	}
	return alias;
}

/**
 * get the alias of the given page represented by the "title"
 * 
 * @param title
 * 				The title of the given page
 * @return
 * 				A map with 0,1,2,3 fields storing the orginal name, ambiguous titles, redirect titles and unique anchor texts respectively
 */
private Map GetEntityAlias(String title)  
{
	Map alias = new HashMap<>();
	alias.put(0,title);
	List<String> list; 
	list = this.wiki.GetAmbigTitlesOfPage(title); // get title whose ambiugous titles include given title 
	alias.put(1,list);
	list = this.wiki.GetRedirectedTitlesOfPage(title); // get titles which redirect to the given title
	alias.put(2,list);
	list = this.wiki.GetLinkedInAnchorsOfPage(title);
	list = Unique.Unique(list);
	alias.put(3,list);
	return alias;
}

	
}
