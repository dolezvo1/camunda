/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';
import {RouteComponentProps} from 'react-router';

import {Tabs, Typeahead} from 'components';
import {loadReports} from 'services';

import {CreateTileModal} from './CreateTileModal';

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    loadReports: jest.fn().mockReturnValue([]),
  };
});

const props = {
  ...({location: {pathname: '/dashboard/1'}} as RouteComponentProps),
  close: jest.fn(),
  confirm: jest.fn(),
};

it('should load the available reports', () => {
  shallow(<CreateTileModal {...props} />);

  runAllEffects();

  expect(loadReports).toHaveBeenCalled();
});

it('should load only reports in the same collection', () => {
  const locationProps = {
    location: {pathname: '/collection/123/dashboard/1'},
  } as RouteComponentProps;
  shallow(<CreateTileModal {...props} {...locationProps} />);

  runAllEffects();

  expect(loadReports).toHaveBeenCalledWith('123');
});

it('should render a Typeahead element with the available reports as options', async () => {
  (loadReports as jest.Mock).mockReturnValueOnce([
    {
      id: 'a',
      name: 'Report A',
    },
    {
      id: 'b',
      name: 'Report B',
    },
  ]);
  const node = shallow(<CreateTileModal {...props} />);

  runAllEffects();
  await flushPromises();

  expect(node.find('Typeahead')).toMatchSnapshot();
});

it('should call the callback when adding a report', async () => {
  (loadReports as jest.Mock).mockReturnValueOnce([
    {
      id: 'a',
      name: 'Report A',
    },
    {
      id: 'b',
      name: 'Report B',
    },
  ]);
  const spy = jest.fn();
  const node = shallow(<CreateTileModal {...props} confirm={spy} />);

  runAllEffects();
  await flushPromises();

  node.find(Typeahead).prop('onChange')('a');

  node.find('Button').at(1).simulate('click');

  expect(spy).toHaveBeenCalledWith({
    id: 'a',
    type: 'optimize_report',
  });
});

it('should show a loading message while loading available reports', () => {
  const node = shallow(<CreateTileModal {...props} />);

  expect(node.find('LoadingIndicator')).toExist();
});

it('should contain an External Website field', () => {
  const node = shallow(<CreateTileModal {...props} />);

  expect(node.find(Tabs.Tab).at(1).prop('title')).toBe('External Website');
});

it('should hide the typeahead when external mode is enabled', () => {
  const node = shallow(<CreateTileModal {...props} />);

  node.find(Tabs).simulate('change', 'external');

  expect(node.find('Typeahead')).not.toExist();
});

it('should contain a text input field if in external source mode', () => {
  const node = shallow(<CreateTileModal {...props} />);

  node.find(Tabs).simulate('change', 'external_url');

  expect(node.find('.externalInput')).toExist();
});

it('should  disable the submit button if the url does not start with http in external mode', () => {
  const node = shallow(<CreateTileModal {...props} />);

  node.find(Tabs).simulate('change', 'external_url');
  node.find('.externalInput').simulate('change', {
    target: {value: 'Dear computer, please show me a report. Thanks.'},
  });

  expect(node.find('Button').at(1)).toBeDisabled();
});

it('should contain an Text field', () => {
  const node = shallow(<CreateTileModal {...props} />);

  expect(node.find(Tabs.Tab).at(2).prop('title')).toBe('Text');
});

it('should contain text editor if in text report mode', () => {
  const node = shallow(<CreateTileModal {...props} />);

  node.find(Tabs).simulate('change', 'text');

  expect(node.find('TextEditor')).toExist();
});

it('should  disable the submit button if the text in editor is empty or too long', () => {
  const node = shallow(<CreateTileModal {...props} />);

  node.find(Tabs).simulate('change', 'text');

  expect(node.find('Button').at(1)).toBeDisabled();

  const normalText = {
    root: {
      children: [
        {
          children: [
            {
              text: 'a'.repeat(10),
            },
          ],
          type: 'paragraph',
        },
      ],
      type: 'root',
    },
  };
  node.find('TextEditor').simulate('change', normalText);
  expect(node.find('Button').at(1)).not.toBeDisabled();

  const tooLongText = {
    root: {
      children: [
        {
          children: [
            {
              text: 'a'.repeat(3001),
            },
          ],
          type: 'paragraph',
        },
      ],
      type: 'root',
    },
  };

  node.find('TextEditor').simulate('change', tooLongText);

  expect(node.find('Button').at(1)).toBeDisabled();
});
