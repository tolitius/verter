.PHONY: clean aot jar tag outdated install deploy tree test repl

clean:
	rm -rf target
	rm -rf classes

aot:
	mkdir classes

jar: clean aot tag
	clojure -A:jar

outdated:
	clojure -M:outdated

tag:
	clojure -A:tag

install: jar
	clojure -A:install

deploy: jar
	clojure -A:deploy

tree:
	mvn dependency:tree

test:
	clojure -X:test :patterns '[".*test"]'

repl:
	clojure -A:dev -A:repl
