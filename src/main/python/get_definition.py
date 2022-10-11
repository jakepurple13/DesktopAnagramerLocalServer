#!/usr/bin/python3

import sys
from CoreServices import DictionaryServices

def main():
    try:
        searchword = sys.argv[1]
    except IndexError:
        errmsg = 'You did not enter any terms to look up in the Dictionary.'
        print(errmsg)
        sys.exit()
    wordrange = (0, len(searchword))
    dictresult = DictionaryServices.DCSCopyTextDefinition(None, searchword, wordrange)
    if not dictresult:
        errmsg = "'%s' not found in Dictionary." % (searchword)
        print(errmsg)
    else:
        print(dictresult)


if __name__ == '__main__':
    main()