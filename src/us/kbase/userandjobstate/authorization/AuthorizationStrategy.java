package us.kbase.userandjobstate.authorization;

/** An authorization strategy to use for the UJS. Can any string that the
 * UJSAuthorizer understands.
 * @author gaprice@lbl.gov
 *
 */
public class AuthorizationStrategy {

	private final String strat;
	
	/** Create a strategy.
	 * @param strategy the name of the strategy.
	 */
	public AuthorizationStrategy(final String strategy) {
		if (strategy == null || strategy.isEmpty()) {
			throw new IllegalArgumentException(
					"strategy cannot be null or empty");
		}
		strat = strategy;
	}

	/** Get the strategy name.
	 * @return the strategy name.
	 */
	public String getStrat() {
		return strat;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("AuthorizationStrategy [strat=");
		builder.append(strat);
		builder.append("]");
		return builder.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((strat == null) ? 0 : strat.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AuthorizationStrategy other = (AuthorizationStrategy) obj;
		if (strat == null) {
			if (other.strat != null)
				return false;
		} else if (!strat.equals(other.strat))
			return false;
		return true;
	}
	
}
