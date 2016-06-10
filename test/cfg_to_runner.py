#!/usr/bin/env python
'''
Created on Oct 1, 2013

@author: gaprice@lbl.gov
'''
from configobj import ConfigObj
import os
import sys

ANT = 'ant'

CFG_SECTION = 'UserJobTest'

CONFIG_OPTS = ['test.user1',
               'test.pwd1',
               'test.user2',
               'test.pwd2',
               'test.user3',
               'test.pwd3',
               'test.mongo.exe',
               'test.temp.dir',
               'test.temp.dir.keep'
               ]

if __name__ == '__main__':
    d, _ = os.path.split(os.path.abspath(__file__))
    fn = 'test.cfg'
    if len(sys.argv) > 1:
        fn = sys.argv[1]
    fn = os.path.join(d, fn)
    if not os.path.isfile(fn):
        print 'No such config file ' + fn + '. Halting.'
        sys.exit(1)
    print 'Using test config file ' + fn
    out = os.path.join(d, 'run_tests.sh')
    cfg = ConfigObj(fn)
    try:
        testcfg = cfg[CFG_SECTION]
    except KeyError as ke:
        print 'Test config file ' + fn + ' is missing section ' +\
            CFG_SECTION + '. Halting.'
        sys.exit(1)
    if testcfg['test.user1'] == testcfg['test.user2']:
        print "Test users 1 & 2 are identical. Halting."
        sys.exit(1)
    if testcfg['test.user1'] == testcfg['test.user3']:
        print "Test users 1 & 3 are identical. Halting."
        sys.exit(1)
    if testcfg['test.user2'] == testcfg['test.user3']:
        print "Test users 2 & 3 are identical. Halting."
        sys.exit(1)
    with open(out, 'w') as run:
        run.write('# Generated file - do not check into git\n')
#        run.write('cd ..\n')
        run.write(ANT + ' test')
        for o in CONFIG_OPTS:
            if o in testcfg:
                run.write(' -D' + o + '=' + testcfg[o])
        run.write('\n')
    os.chmod(out, 0755)
