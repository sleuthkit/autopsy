all:
	@sh ./configure

conf.pl:
	@sh ./configure

live: conf.pl
	./make-live-cd

indent:
	cd lib; perltidy -b *.pm *.pl
	cd base; perltidy -b autopsy.base

clean:
	rm -f ./autopsy
	rm -f ./make-live-cd
	rm -f ./conf.pl
	rm -f ./config.tmp
	rm -f ./config2.tmp
	rm -rf ./live-cd/
	rm -f ./lib/*.bak
	rm -f ./base/*.bak
	find . -name ".DS_Store" | xargs rm -f
	find . -type f -perm +g+x,o+x,u+x | xargs  chmod -x
	grep "curtskver=" ./configure
	grep "VER" ./lib/define.pl
	find . -name ".*" | grep -v perltidy

release:
	find . -name "CVS" | xargs rm -rf
